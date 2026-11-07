package app.aaps.core.keys.interfaces

import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.IntNonKey
import app.aaps.core.keys.UnitDoubleKey
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The check that the settings import and the remote preference sync use before they store a number.
 * A wrong "fine" here lets a NaN or out-of-range safety limit into the store.
 */
class PreferenceValueCheckTest {

    @Test
    fun nanAndInfinityAreRejected() {
        assertNotNull(PreferenceValueCheck.rejectReason(DoubleKey.ApsSmbMaxIob, Double.NaN))
        assertNotNull(PreferenceValueCheck.rejectReason(DoubleKey.SafetyMaxBolus, Double.POSITIVE_INFINITY))
        assertNotNull(PreferenceValueCheck.rejectReason(DoubleKey.ApsMaxDailyMultiplier, Double.NEGATIVE_INFINITY))
        assertNotNull(PreferenceValueCheck.rejectReason(UnitDoubleKey.OverviewLowMark, Double.NaN))
    }

    @Test
    fun doubleOutsideRangeIsRejected() {
        // ApsMaxDailyMultiplier: 1.0 .. 10.0
        assertNotNull(PreferenceValueCheck.rejectReason(DoubleKey.ApsMaxDailyMultiplier, 0.99))
        assertNotNull(PreferenceValueCheck.rejectReason(DoubleKey.ApsMaxDailyMultiplier, 10.01))
        // ApsSmbMaxIob: 0.0 .. 70.0
        assertNotNull(PreferenceValueCheck.rejectReason(DoubleKey.ApsSmbMaxIob, -0.1))
        assertNotNull(PreferenceValueCheck.rejectReason(DoubleKey.ApsSmbMaxIob, 70.1))
    }

    @Test
    fun doubleInsideRangeIsFine() {
        assertNull(PreferenceValueCheck.rejectReason(DoubleKey.ApsMaxDailyMultiplier, 1.0))
        assertNull(PreferenceValueCheck.rejectReason(DoubleKey.ApsMaxDailyMultiplier, 10.0))
        assertNull(PreferenceValueCheck.rejectReason(DoubleKey.ApsSmbMaxIob, 0.0))
        assertNull(PreferenceValueCheck.rejectReason(DoubleKey.ApsSmbMaxIob, 3.0))
    }

    @Test
    fun intOutsideRangeIsRejected() {
        // ApsMaxSmbFrequency: 1 .. 10
        assertNotNull(PreferenceValueCheck.rejectReason(IntKey.ApsMaxSmbFrequency, 0))
        assertNotNull(PreferenceValueCheck.rejectReason(IntKey.ApsMaxSmbFrequency, 11))
        // ApsMaxMinutesOfBasalToLimitSmb: 15 .. 120
        assertNotNull(PreferenceValueCheck.rejectReason(IntKey.ApsMaxMinutesOfBasalToLimitSmb, 1000))
    }

    @Test
    fun intInsideRangeIsFine() {
        assertNull(PreferenceValueCheck.rejectReason(IntKey.ApsMaxSmbFrequency, 1))
        assertNull(PreferenceValueCheck.rejectReason(IntKey.ApsMaxSmbFrequency, 10))
        assertNull(PreferenceValueCheck.rejectReason(IntKey.ApsMaxMinutesOfBasalToLimitSmb, 120))
    }

    @Test
    fun keyWithoutRangeOnlyNeedsAValue() {
        assertNull(PreferenceValueCheck.rejectReason(IntNonKey.TddCycleOffset, Int.MAX_VALUE))
        assertNull(PreferenceValueCheck.rejectReason(IntNonKey.TddCycleOffset, Int.MIN_VALUE))
    }

    /** Stored as mg/dl, but older builds stored mmol/l and the read side still accepts both. */
    @Test
    fun unitDoubleAcceptsMgdlAndMmolInRange() {
        // OverviewLowMark: 25 .. 160 mg/dl
        assertNull(PreferenceValueCheck.rejectReason(UnitDoubleKey.OverviewLowMark, 72.0))
        assertNull(PreferenceValueCheck.rejectReason(UnitDoubleKey.OverviewLowMark, 4.0))
        assertNotNull(PreferenceValueCheck.rejectReason(UnitDoubleKey.OverviewLowMark, 400.0))
        assertNotNull(PreferenceValueCheck.rejectReason(UnitDoubleKey.OverviewLowMark, 0.5))
    }

    /** Every built-in key must accept its own default, or a plain export and import would lose it. */
    @Test
    fun everyDefaultIsInsideItsRange() {
        DoubleKey.entries.forEach { assertNull(PreferenceValueCheck.rejectReason(it, it.defaultValue), it.name) }
        IntKey.entries.forEach { assertNull(PreferenceValueCheck.rejectReason(it, it.defaultValue), it.name) }
        UnitDoubleKey.entries.forEach { assertNull(PreferenceValueCheck.rejectReason(it, it.defaultValue), it.name) }
    }
}
