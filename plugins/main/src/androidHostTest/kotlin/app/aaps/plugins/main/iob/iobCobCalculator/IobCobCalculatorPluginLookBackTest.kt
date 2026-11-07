package app.aaps.plugins.main.iob.iobCobCalculator

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.EB
import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.EffectiveProfile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.workflow.CalculationSignalsEmitter
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.shared.tests.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Each bolus keeps the insulin it was given with. After a switch to an insulin with a shorter DIA, an older
 * bolus with a longer DIA must still count in IOB until its own DIA has passed.
 */
class IobCobCalculatorPluginLookBackTest {

    private val persistenceLayer = mock<PersistenceLayer>()
    private val profileFunction = mock<ProfileFunction>()
    private val activePlugin = mock<ActivePlugin>()
    private val dateUtil = mock<DateUtil>()
    private val preferences = mock<Preferences>()
    private val currentProfile = mock<EffectiveProfile>()
    private lateinit var sut: IobCobCalculatorPlugin

    private val now = 1_800_000_000_000L
    private val hour = 60 * 60 * 1000L

    private val longInsulin = ICfg(insulinLabel = "long", peak = 75, dia = 9.0, concentration = 1.0)
    private val shortInsulin = ICfg(insulinLabel = "short", peak = 55, dia = 5.0, concentration = 1.0)

    @BeforeEach
    fun setUp() = runBlocking {
        whenever(dateUtil.now()).thenReturn(now)
        whenever(preferences.get(DoubleKey.ApsAmaBolusSnoozeDivisor)).thenReturn(2.0)
        val pump = mock<PumpWithConcentration>()
        whenever(pump.isFakingTempsByExtendedBoluses).thenReturn(false)
        whenever(activePlugin.activePump).thenReturn(pump)
        // The current profile uses the short insulin
        whenever(currentProfile.iCfg).thenReturn(shortInsulin)
        whenever(profileFunction.getProfile()).thenReturn(currentProfile)
        whenever(persistenceLayer.getBolusesFromTime(any(), any())).thenReturn(emptyList())
        whenever(persistenceLayer.getExtendedBolusesStartingFromTimeToTime(any(), any(), any())).thenReturn(emptyList())
        sut = IobCobCalculatorPlugin(
            AAPSLoggerTest(), mock<RxBus>(), preferences, mock<TextResolver>(), profileFunction, activePlugin, dateUtil,
            persistenceLayer, mock<OverviewData>(), mock<CalculationWorkflow>(), mock<DecimalFormatter>(), mock<ProcessedTbrEbData>(),
            mock<CalculationSignalsEmitter>(), mock<NotificationManager>()
        ) { mock<OverviewDataCache>() }
    }

    @Test
    fun `bolus with long DIA still counts after a switch to an insulin with short DIA`() = runBlocking {
        // Given 7 h ago with the 9 h insulin: still active, but older than the 5 h of the current insulin
        val bolus = BS(timestamp = now - 7 * hour, amount = 5.0, type = BS.Type.NORMAL, iCfg = longInsulin)
        // Like the database: only boluses at or after the asked time
        whenever(persistenceLayer.getBolusesFromTime(any(), eq(true))).thenAnswer { invocation ->
            val from = invocation.arguments[0] as Long
            listOf(bolus).filter { it.timestamp >= from }
        }

        val iob = sut.calculateIobFromBolus()

        assertThat(iob.iob).isGreaterThan(0.0)
    }

    @Test
    fun `extended bolus without a profile does not hide the following extended boluses`() = runBlocking {
        val noProfileTime = now - 3 * hour
        val withProfileTime = now - 2 * hour
        val first = EB(timestamp = noProfileTime, duration = hour / 2, amount = 1.0)
        val second = EB(timestamp = withProfileTime, duration = hour / 2, amount = 1.0)
        whenever(persistenceLayer.getExtendedBolusesStartingFromTimeToTime(any(), any(), eq(true))).thenReturn(listOf(first, second))
        whenever(profileFunction.getProfile(noProfileTime)).thenReturn(null)
        whenever(profileFunction.getProfile(withProfileTime)).thenReturn(currentProfile)

        val iob = sut.calculateIobFromBolus()

        assertThat(iob.iob).isGreaterThan(0.0)
    }
}
