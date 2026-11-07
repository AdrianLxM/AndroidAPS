package app.aaps.plugins.aps.loop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BasalPercentTest {

    @Test
    fun rateIsGivenAsPercentOfBasal() {
        assertEquals(150, basalPercent(rate = 1.5, basal = 1.0))
        assertEquals(0, basalPercent(rate = 0.0, basal = 0.8))
        assertEquals(100, basalPercent(rate = 0.8, basal = 0.8))
    }

    @Test
    fun aZeroBasalGivesNoPercent() {
        // The plain division gave Int.MAX_VALUE here
        assertNull(basalPercent(rate = 2.0, basal = 0.0))
        // and 0 here (NaN.toInt())
        assertNull(basalPercent(rate = 0.0, basal = 0.0))
    }

    @Test
    fun aNegativeOrNaNBasalGivesNoPercent() {
        assertNull(basalPercent(rate = 1.0, basal = -0.5))
        assertNull(basalPercent(rate = 1.0, basal = Double.NaN))
    }
}
