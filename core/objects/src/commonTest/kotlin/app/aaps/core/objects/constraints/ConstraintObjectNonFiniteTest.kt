package app.aaps.core.objects.constraints

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * NaN and Infinity must never pass the constraint layer. `x < NaN` and `x > NaN` are both false, so
 * without a guard a NaN rate or SMB passed "must be positive", max basal, the hard limit and the pump
 * limit unchanged. This is the last step before the pump.
 */
class ConstraintObjectNonFiniteTest {

    private val logger = RecordingLogger()

    /** The same calls that `SafetyPlugin.applyBasalConstraints` makes for an absolute rate. */
    private fun limitRate(rate: ConstraintObject<Double>) {
        rate.setIfGreater(0.0, "must be positive", this)
        rate.setIfSmaller(10.0, "max basal", this)
        rate.setIfSmaller(15.0, "hard limit", this)
    }

    @Test
    fun nanRateBecomesZero() {
        val rate = ConstraintObject(Double.NaN, logger)
        limitRate(rate)
        assertEquals(0.0, rate.value())
        assertTrue(logger.errors.isNotEmpty())
    }

    @Test
    fun positiveInfinityRateBecomesZeroNotTheLimit() {
        val rate = ConstraintObject(Double.POSITIVE_INFINITY, logger)
        limitRate(rate)
        assertEquals(0.0, rate.value())
    }

    @Test
    fun negativeInfinityRateBecomesZero() {
        val rate = ConstraintObject(Double.NEGATIVE_INFINITY, logger)
        limitRate(rate)
        assertEquals(0.0, rate.value())
    }

    @Test
    fun nanFromSetBecomesZero() {
        val smb = ConstraintObject(1.0, logger)
        smb.set(Double.NaN)
        assertEquals(0.0, smb.value())
        smb.set(1.0)
        smb.set(Double.NaN, "rounding", this)
        assertEquals(0.0, smb.value())
        smb.set(1.0)
        smb.setIfDifferent(Double.NaN, "rounding", this)
        assertEquals(0.0, smb.value())
        assertEquals(3, logger.errors.size)
    }

    @Test
    fun originalValueStillShowsWhatCameIn() {
        val rate = ConstraintObject(Double.NaN, logger)
        assertTrue(rate.originalValue().isNaN())
    }

    /** A NaN limit (for example from a NaN preference) must not remove the other limits. */
    @Test
    fun nanLimitIsIgnoredAndOtherLimitsStillApply() {
        val bolus = ConstraintObject(30.0, logger)
        bolus.setIfSmaller(Double.NaN, "max bolus in preferences", this)
        assertEquals(30.0, bolus.value())
        bolus.setIfSmaller(17.0, "hard limit", this)
        assertEquals(17.0, bolus.value())
        bolus.setIfGreater(Double.NaN, "must be positive", this)
        assertEquals(17.0, bolus.value())
        assertEquals(2, logger.errors.size)
    }

    @Test
    fun negativeInfinityLimitDoesNotDriveTheValueToInfinity() {
        val rate = ConstraintObject(2.0, logger)
        rate.setIfSmaller(Double.NEGATIVE_INFINITY, "broken limit", this)
        assertEquals(2.0, rate.value())
    }

    @Test
    fun floatIsGuardedToo() {
        val f = ConstraintObject(Float.NaN, logger)
        assertEquals(0f, f.value())
    }

    @Test
    fun finiteValuesWorkAsBefore() {
        val rate = ConstraintObject(12.0, logger)
        limitRate(rate)
        assertEquals(10.0, rate.value())
        val negative = ConstraintObject(-1.0, logger)
        limitRate(negative)
        assertEquals(0.0, negative.value())
        val max = ConstraintObject(Double.MAX_VALUE, logger)
        limitRate(max)
        assertEquals(10.0, max.value())
        assertTrue(logger.errors.isEmpty())
    }

    private class RecordingLogger : AAPSLogger {

        val errors = mutableListOf<String>()

        override fun debug(message: String) {}
        override fun debug(enable: Boolean, tag: LTag, message: String) {}
        override fun debug(tag: LTag, message: String) {}
        override fun debug(tag: LTag, accessor: () -> String) {}
        override fun debug(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun warn(tag: LTag, message: String) {}
        override fun warn(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun info(tag: LTag, message: String) {}
        override fun info(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun error(tag: LTag, message: String) { errors += message }
        override fun error(tag: LTag, message: String, throwable: Throwable) { errors += message }
        override fun error(tag: LTag, format: String, vararg arguments: Any?) { errors += format }
        override fun error(message: String) { errors += message }
        override fun error(message: String, throwable: Throwable) { errors += message }
        override fun error(format: String, vararg arguments: Any?) { errors += format }
        override fun debug(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun info(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun warn(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun error(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) { errors += message }
    }
}
