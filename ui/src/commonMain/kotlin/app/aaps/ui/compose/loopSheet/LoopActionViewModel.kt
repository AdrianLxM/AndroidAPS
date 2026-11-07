package app.aaps.ui.compose.loopSheet

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.RM
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAcceptOpenLoopChange
import app.aaps.core.interfaces.rx.events.EventLoopUpdateGui
import app.aaps.core.interfaces.rx.events.EventNewOpenLoopNotification
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.ui.UiStrings
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class LoopActionUiState(
    val actionAvailable: Boolean = false,
    val request: String = "",
    val reason: String = "",
    /** `lastAPSRun` of the shown suggestion. Accept sends it, so a newer suggestion is not enacted in its place. */
    val suggestionTime: Long = 0L
)

// Registers itself: @ViewModelKey infers the key from the class. No graph entry, and deliberately
// unscoped so each screen gets its own.
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
@Inject
class LoopActionViewModel(
    private val loop: Loop,
    private val activePlugin: ActivePlugin,
    private val profileFunction: ProfileFunction,
    private val rh: TextResolver,
    rxBus: RxBus
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoopActionUiState())
    val uiState: StateFlow<LoopActionUiState> = _uiState.asStateFlow()

    init {
        rxBus.toFlow(EventLoopUpdateGui::class)
            .onEach { refreshState() }.launchIn(viewModelScope)
        rxBus.toFlow(EventNewOpenLoopNotification::class)
            .onEach { refreshState() }.launchIn(viewModelScope)
        rxBus.toFlow(EventRefreshOverview::class)
            .onEach { refreshState() }.launchIn(viewModelScope)
        rxBus.toFlow(EventAcceptOpenLoopChange::class)
            .onEach { refreshState() }.launchIn(viewModelScope)
        refreshState()
    }

    fun refreshState() {
        viewModelScope.launch { refresh() }
    }

    private suspend fun refresh() {
        val pump = activePlugin.activePump
        val profile = profileFunction.getProfile()
        val lastRun = loop.lastRun
        // A loop run writes the new result into the same LastRun object. Read the time first and the
        // shown text right after it, before any suspension, so the text belongs to that time or to a
        // newer run - and a newer run makes the accept refuse instead of enacting unseen text.
        val suggestionTime = lastRun?.lastAPSRun ?: 0L
        val constraintsProcessed = lastRun?.constraintsProcessed
        val lastOpenModeAccept = lastRun?.lastOpenModeAccept ?: 0L

        val resultAvailable = lastRun != null &&
            (lastOpenModeAccept == 0L || lastOpenModeAccept < suggestionTime) &&
            constraintsProcessed?.isChangeRequested() == true

        val available = resultAvailable &&
            pump.isInitialized() &&
            profile != null &&
            loop.runningMode() == RM.Mode.OPEN_LOOP &&
            (loop as PluginBase).isEnabled()

        _uiState.update {
            if (available) {
                LoopActionUiState(
                    actionAvailable = true,
                    request = constraintsProcessed?.resultAsString().orEmpty(),
                    reason = rh.gs(UiStrings.loop_accept_set_basal_question),
                    suggestionTime = suggestionTime
                )
            } else {
                LoopActionUiState(actionAvailable = false)
            }
        }
    }
}
