package app.aaps.core.data.model

import app.aaps.core.data.time.T
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [RM.end] and [RM.isEndless] must agree on both "endless" durations and must never overflow.
 */
class RMTest {

    private fun rm(duration: Long, mode: RM.Mode = RM.Mode.SUSPENDED_BY_USER) =
        RM(timestamp = START, utcOffset = 0, mode = mode, duration = duration)

    @Test
    fun finiteModeEndsAtTimestampPlusDuration() {
        val mode = rm(T.mins(30).msecs())
        assertFalse(mode.isEndless)
        assertTrue(mode.isTemporary())
        assertEquals(START + T.mins(30).msecs(), mode.end)
    }

    @Test
    fun permanentModeHasNoEnd() {
        val mode = rm(0, RM.Mode.CLOSED_LOOP)
        assertTrue(mode.isEndless)
        assertFalse(mode.isTemporary())
        assertEquals(Long.MAX_VALUE, mode.end)
    }

    @Test
    fun forcedModeWithLongMaxValueHasNoEndAndDoesNotOverflow() {
        val mode = rm(RM.ENDLESS_DURATION, RM.Mode.CLOSED_LOOP_LGS)
        assertTrue(mode.isEndless)
        // Stays in the temporary layer of the "active at" queries
        assertTrue(mode.isTemporary())
        // A plain timestamp + duration would wrap to a negative number here
        assertTrue(START + mode.duration < 0)
        assertEquals(Long.MAX_VALUE, mode.end)
    }

    @Test
    fun pumpSuspendWithIntMaxValueMinutesHasNoEnd() {
        // LoopPlugin.runningModePreCheck stores Int.MAX_VALUE minutes for SUSPENDED_BY_PUMP
        assertEquals(T.mins(Int.MAX_VALUE.toLong()).msecs(), RM.PUMP_SUSPEND_DURATION)
        val mode = rm(T.mins(Int.MAX_VALUE.toLong()).msecs(), RM.Mode.SUSPENDED_BY_PUMP)
        assertTrue(mode.isEndless)
        assertTrue(mode.isTemporary())
        assertEquals(Long.MAX_VALUE, mode.end)
    }

    @Test
    fun pumpSuspendDurationSurvivesJson() {
        // Nightscout stores numbers as JavaScript doubles, which are exact only up to 2^53
        assertTrue(RM.PUMP_SUSPEND_DURATION < (1L shl 53))
    }

    @Test
    fun everyDurationFromPumpSuspendDurationUpIsEndless() {
        assertFalse(rm(RM.PUMP_SUSPEND_DURATION - 1).isEndless)
        assertEquals(START + RM.PUMP_SUSPEND_DURATION - 1, rm(RM.PUMP_SUSPEND_DURATION - 1).end)
        assertTrue(rm(RM.PUMP_SUSPEND_DURATION + 1).isEndless)
        assertTrue(rm(Long.MAX_VALUE - 1_000_000L).isEndless)
        assertEquals(Long.MAX_VALUE, rm(Long.MAX_VALUE - 1_000_000L).end)
    }

    @Test
    fun endIsNotPartOfContentEquality() {
        // end and isEndless are derived, so they must not change what contentEqualsTo compares
        assertTrue(rm(RM.ENDLESS_DURATION).contentEqualsTo(rm(RM.ENDLESS_DURATION)))
        assertFalse(rm(RM.ENDLESS_DURATION).contentEqualsTo(rm(RM.PUMP_SUSPEND_DURATION)))
    }

    private companion object {

        const val START = 1_700_000_000_000L
    }
}
