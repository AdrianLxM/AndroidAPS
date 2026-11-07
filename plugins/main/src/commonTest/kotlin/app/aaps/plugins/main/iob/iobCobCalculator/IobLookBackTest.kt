package app.aaps.plugins.main.iob.iobCobCalculator

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.iobCalc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The IOB calculation reads boluses, temp basals and extended boluses from `now - iobLookBack(...)`.
 * Each of them keeps its own insulin, so the window must not depend on the DIA of the current profile only.
 */
class IobLookBackTest {

    private val hour = 60 * 60 * 1000L
    private val now = 1_800_000_000_000L

    @Test
    fun bolusWithLongDiaStillCountsAfterSwitchToShortDia() {
        // Bolus given 7 h ago with a 9 h insulin. The current profile has a 5 h insulin.
        val longInsulin = ICfg(insulinLabel = "long", peak = 75, dia = 9.0, concentration = 1.0)
        val shortInsulin = ICfg(insulinLabel = "short", peak = 55, dia = 5.0, concentration = 1.0)
        val bolus = BS(timestamp = now - 7 * hour, amount = 5.0, type = BS.Type.NORMAL, iCfg = longInsulin)

        // The bolus still has insulin on board ...
        assertTrue(bolus.iobCalc(now).iobContrib > 0.0)
        // ... so it must be inside the window the boluses are read from.
        val from = now - IobCobCalculatorPlugin.iobLookBack(shortInsulin.insulinEndTime)
        assertTrue(bolus.timestamp >= from, "bolus at -7 h must be inside the window that starts at ${(from - now) / hour} h")
    }

    @Test
    fun windowCoversTheLongestDiaTheHardLimitsAllow() {
        assertEquals(10 * hour, IobCobCalculatorPlugin.MAX_INSULIN_END_TIME)
        assertEquals(10 * hour, IobCobCalculatorPlugin.iobLookBack(5 * hour))
        assertEquals(10 * hour, IobCobCalculatorPlugin.iobLookBack(null))
    }

    @Test
    fun windowIsNeverShorterThanTheCurrentDia() {
        assertEquals(12 * hour, IobCobCalculatorPlugin.iobLookBack(12 * hour))
    }
}
