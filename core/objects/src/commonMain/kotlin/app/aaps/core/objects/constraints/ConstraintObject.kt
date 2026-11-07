package app.aaps.core.objects.constraints

import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag

/**
 * A value (rate, bolus, max IOB, a switch, ...) that the constraint plugins limit, with the reasons why.
 *
 * ## NaN and Infinity
 *
 * A Double value is never allowed to be NaN or Infinity here. `value < NaN` and `value > NaN` are both
 * false, so a NaN rate or SMB would pass "must be positive", max basal, the hard limit and the pump
 * limit unchanged, and `Round.floorTo(NaN, step)` stays NaN. This class is the last step before the
 * pump, so it must not depend on every APS plugin being careful.
 *
 * - A non-finite value that comes in (constructor, [set], [setIfDifferent]) is replaced by 0. Zero,
 *   and not the limit, because for rates and boluses 0 is the value that gives the least insulin; a
 *   NaN SMB that became "the max bolus" would be the worst choice. For max IOB 0 is the most
 *   limiting value too. [originalValue] keeps what came in, so the log still shows it.
 * - A non-finite limit given to [setIfSmaller] or [setIfGreater] is ignored, because nothing can be
 *   learned from it, and the other limits still apply.
 *
 * Both cases are logged as an error.
 */
class ConstraintObject<T : Comparable<T>>(value: T, private val aapsLogger: AAPSLogger) : Constraint<T> {

    private var value: T = finiteOrZero(value, "initial value")

    private var originalValue: T
    private val reasons: MutableList<String> = ArrayList()
    private val mostLimiting: MutableList<String> = ArrayList()
    override fun value(): T {
        return value
    }

    override fun originalValue(): T {
        return originalValue
    }

    override fun set(value: T): Constraint<T> {
        this.value = finiteOrZero(value, "set")
        originalValue = value
        aapsLogger.debug(LTag.CONSTRAINTS, "Setting value $value")
        return this
    }

    override fun set(value: T, reason: String, from: Any): Constraint<T> {
        aapsLogger.debug(LTag.CONSTRAINTS, "Setting value " + this.value + " -> " + value + " (" + reason + ")[" + translateFrom(from) + "]")
        this.value = finiteOrZero(value, reason)
        addReason(reason, from)
        addMostLimingReason(reason, from)
        return this
    }

    override fun setIfDifferent(value: T, reason: String, from: Any): Constraint<T> {
        if (this.value != value) {
            aapsLogger.debug(LTag.CONSTRAINTS, "Setting because of different value " + this.value + " -> " + value + " (" + reason + ")[" + translateFrom(from) + "]")
            this.value = finiteOrZero(value, reason)
            addReason(reason, from)
            addMostLimingReason(reason, from)
        }
        return this
    }

    override fun setIfSmaller(value: T, reason: String, from: Any): Constraint<T> {
        if (isNotFinite(value)) {
            aapsLogger.error(LTag.CONSTRAINTS, "Ignoring limit $value, it is not a finite number (" + reason + ")[" + translateFrom(from) + "]")
            return this
        }
        if (value < this.value) {
            aapsLogger.debug(LTag.CONSTRAINTS, "Setting because of smaller value " + this.value + " -> " + value + " (" + reason + ")[" + translateFrom(from) + "]")
            this.value = value
            mostLimiting.clear()
            addMostLimingReason(reason, from)
        }
        if (value < originalValue) {
            addReason(reason, from)
        }
        return this
    }

    override fun setIfGreater(value: T, reason: String, from: Any): Constraint<T> {
        if (isNotFinite(value)) {
            aapsLogger.error(LTag.CONSTRAINTS, "Ignoring limit $value, it is not a finite number (" + reason + ")[" + translateFrom(from) + "]")
            return this
        }
        if (value > this.value) {
            aapsLogger.debug(LTag.CONSTRAINTS, "Setting because of greater value " + this.value + " -> " + value + " (" + reason + ")[" + translateFrom(from) + "]")
            this.value = value
            mostLimiting.clear()
            addMostLimingReason(reason, from)
        }
        if (value > originalValue) {
            addReason(reason, from)
        }
        return this
    }

    private fun isNotFinite(value: T): Boolean =
        (value is Double && !value.isFinite()) || (value is Float && !value.isFinite())

    /** [value], or 0 of the same type when it is a NaN or infinite Double or Float. See the class doc. */
    @Suppress("UNCHECKED_CAST")
    private fun finiteOrZero(value: T, where: String): T {
        if (!isNotFinite(value)) return value
        aapsLogger.error(LTag.CONSTRAINTS, "Value $value is not a finite number ($where), using 0 instead")
        return when (value) {
            is Float -> 0f as T
            else     -> 0.0 as T
        }
    }

    private fun translateFrom(from: Any): String {
        return from::class.simpleName.orEmpty().replace("Plugin", "")
    }

    override fun addReason(reason: String, from: Any) {
        reasons.add(translateFrom(from) + ": " + reason)
    }

    private fun addMostLimingReason(reason: String, from: Any) {
        mostLimiting.add(translateFrom(from) + ": " + reason)
    }

    override fun getReasons(): String {
        val sb = StringBuilder()
        for ((count, r) in reasons.withIndex()) {
            if (count != 0) sb.append("\n")
            sb.append(r)
        }
        aapsLogger.debug(LTag.CONSTRAINTS, "Limiting original value: $originalValue to $value. Reason: $sb")
        return sb.toString()
    }

    override val reasonList: List<String>
        get() = reasons

    override fun getMostLimitedReasons(): String {
        val sb = StringBuilder()
        for ((count, r) in mostLimiting.withIndex()) {
            if (count != 0) sb.append("\n")
            sb.append(r)
        }
        aapsLogger.debug(LTag.CONSTRAINTS, "Limiting original value: $originalValue to $value. Reason: $sb")
        return sb.toString()
    }

    override val mostLimitedReasonList: List<String>
        get() = mostLimiting

    override fun copyReasons(another: Constraint<*>) {
        reasons.addAll(another.reasonList)
    }

    init {
        originalValue = value
    }
}