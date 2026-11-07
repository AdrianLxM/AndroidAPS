package app.aaps.plugins.aps.openAPS

import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.profile.EffectiveProfile
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.CoreUiStrings
import app.aaps.shared.tests.HardLimitsMock
import app.aaps.shared.tests.generatedTextResolver
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ApsInputHardLimitsTest {

    private val preferences: Preferences = mock()
    private val resourceHelper: ResourceHelper = mock()
    private val profile: EffectiveProfile = mock()
    private lateinit var rh: TextResolver
    private lateinit var hardLimits: HardLimitsMock

    @BeforeEach
    fun setup() {
        rh = generatedTextResolver()
        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("adult")
        hardLimits = HardLimitsMock(preferences, resourceHelper)
        // Values inside the adult hard limits
        whenever(profile.iCfg).thenReturn(ICfg("Insulin", peak = 75, dia = 6.0, concentration = 1.0))
        whenever(profile.getIcTimeFromMidnight(anyInt())).thenReturn(10.0)
        whenever(profile.getIsfMgdl(any())).thenReturn(50.0)
        whenever(profile.getMaxDailyBasal()).thenReturn(1.0)
    }

    private fun check(currentBasal: Double = 1.0) = checkApsInputHardLimits(hardLimits, rh, profile, currentBasal, "test")

    @Test
    fun `all values inside the limits give no reason`() {
        assertThat(check()).isNull()
    }

    @Test
    fun `DIA out of range names DIA and stops before the other checks`() {
        whenever(profile.iCfg).thenReturn(ICfg("Insulin", peak = 75, dia = 0.5, concentration = 1.0))

        assertThat(check()).isEqualTo(rh.gs(CoreUiStrings.valueoutofrange, rh.gs(InterfacesStrings.profile_dia)))
        verify(profile, never()).getIsfMgdl(any())
    }

    @Test
    fun `ISF out of range names ISF`() {
        whenever(profile.getIsfMgdl(any())).thenReturn(1.0)

        assertThat(check()).isEqualTo(rh.gs(CoreUiStrings.valueoutofrange, rh.gs(InterfacesStrings.profile_sensitivity_value)))
    }

    @Test
    fun `current basal of zero names the current basal`() {
        assertThat(check(currentBasal = 0.0)).isEqualTo(rh.gs(CoreUiStrings.valueoutofrange, rh.gs(CoreUiStrings.current_basal_value)))
    }
}
