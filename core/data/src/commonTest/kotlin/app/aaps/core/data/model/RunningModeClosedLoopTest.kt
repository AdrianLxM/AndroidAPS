package app.aaps.core.data.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * SMB and the "handle change at driver level" path of `isChangeRequested` both use
 * [RM.Mode.isClosedLoopOrLgs] to know if the closed loop is switched on. A new mode must not count as
 * closed loop by accident, because that would turn SMB on in it.
 */
class RunningModeClosedLoopTest {

    @Test
    fun `only CLOSED_LOOP and CLOSED_LOOP_LGS count as closed loop`() {
        val closed = RM.Mode.entries.filter { it.isClosedLoopOrLgs() }
        assertEquals(listOf(RM.Mode.CLOSED_LOOP, RM.Mode.CLOSED_LOOP_LGS), closed)
    }
}
