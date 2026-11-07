package app.aaps.plugins.aps.loop

import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.plugins.aps.ApsStrings

/**
 * Text for the loop screen when the loop got no result from the APS.
 *
 * @param apsEnabled true when an APS plugin is selected and enabled, so it was asked for a result
 * @param reason why the APS gave no result (`APS.lastNoResultReason`), or null when it gave no reason
 */
internal fun noApsResultMessage(apsEnabled: Boolean, reason: String?, rh: TextResolver): String = when {
    !apsEnabled    -> rh.gs(ApsStrings.no_aps_selected)
    reason != null -> rh.gs(ApsStrings.aps_no_result_reason, reason)
    else           -> rh.gs(ApsStrings.aps_no_result)
}
