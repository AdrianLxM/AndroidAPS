package app.aaps.plugins.aps.loop

import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.aps.ApsStrings
import kotlin.test.Test
import kotlin.test.assertEquals

class NoApsResultMessageTest {

    /** Returns the string name and the format arguments, so a test can see which string was picked. */
    private val rh = object : TextResolver {
        override fun gs(ref: TextRef): String = name(ref)
        override fun gs(ref: TextRef, vararg args: Any?): String = (listOf(name(ref)) + args.map { it.toString() }).joinToString("|")
        override fun gsNotLocalised(ref: TextRef): String = name(ref)
        private fun name(ref: TextRef): String = (ref as TextRef.Named).name
    }

    private fun name(ref: TextRef): String = (ref as TextRef.Named).name

    @Test
    fun noApsEnabledStillSaysNoApsSelected() {
        assertEquals(name(ApsStrings.no_aps_selected), noApsResultMessage(apsEnabled = false, reason = null, rh = rh))
    }

    @Test
    fun noApsEnabledIgnoresAnOldReason() {
        // The APS was not asked this time, so a reason from an older run must not be shown.
        assertEquals(name(ApsStrings.no_aps_selected), noApsResultMessage(apsEnabled = false, reason = "old reason", rh = rh))
    }

    @Test
    fun apsStoppedWithReasonShowsTheReason() {
        assertEquals(
            name(ApsStrings.aps_no_result_reason) + "|No glucose data available",
            noApsResultMessage(apsEnabled = true, reason = "No glucose data available", rh = rh)
        )
    }

    @Test
    fun apsStoppedWithoutReasonShowsNeutralText() {
        assertEquals(name(ApsStrings.aps_no_result), noApsResultMessage(apsEnabled = true, reason = null, rh = rh))
    }
}
