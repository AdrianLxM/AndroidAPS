package app.aaps.plugins.aps.openAPS

import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.profile.EffectiveProfile
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.utils.MidnightUtils

/**
 * Checks the profile and pump values that every OpenAPS algorithm needs against the hard limits.
 *
 * The checks run in a fixed order and stop at the first value that is out of range.
 * [HardLimits.checkHardLimits] already warns the user about that value (notification, snackbar and
 * careportal note), so this function only adds the text for the APS and loop screens.
 *
 * @param currentBasal the pump's current base basal rate in U/h
 * @param caller name of the APS plugin, used for logging in [EffectiveProfile.getIsfMgdl]
 * @return null when every value is inside the hard limits, else a text that names the first value
 *   that is out of range
 */
internal fun checkApsInputHardLimits(
    hardLimits: HardLimits,
    rh: TextResolver,
    profile: EffectiveProfile,
    currentBasal: Double,
    caller: String
): String? {
    val outOfRange: TextRef? = when {
        !hardLimits.checkHardLimits(profile.iCfg.dia, InterfacesStrings.profile_dia, hardLimits.diaRange()) -> InterfacesStrings.profile_dia

        !hardLimits.checkHardLimits(
            profile.getIcTimeFromMidnight(MidnightUtils.secondsFromMidnight()),
            InterfacesStrings.profile_carbs_ratio_value,
            hardLimits.icRange()
        ) -> InterfacesStrings.profile_carbs_ratio_value

        !hardLimits.checkHardLimits(profile.getIsfMgdl(caller), InterfacesStrings.profile_sensitivity_value, HardLimits.LIMIT_ISF) -> InterfacesStrings.profile_sensitivity_value
        !hardLimits.checkHardLimits(profile.getMaxDailyBasal(), CoreUiStrings.profile_max_daily_basal_value, 0.02, hardLimits.maxBasal()) -> CoreUiStrings.profile_max_daily_basal_value
        !hardLimits.checkHardLimits(currentBasal, CoreUiStrings.current_basal_value, 0.01, hardLimits.maxBasal()) -> CoreUiStrings.current_basal_value
        else -> null
    }
    return outOfRange?.let { rh.gs(CoreUiStrings.valueoutofrange, rh.gs(it)) }
}
