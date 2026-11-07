package app.aaps.core.keys.interfaces

/**
 * Checks a number that comes from outside this device before it is stored for [NonPreferenceKey].
 *
 * Used by the settings import and by the preference sync from a remote client. Both parse text, and
 * text can hold anything: "NaN" and "Infinity" parse fine as a double, and a value can be outside the
 * range that the settings screen allows. Such a value must not be stored: for example a NaN max IOB
 * makes `setIfSmaller(NaN)` do nothing, so the user's own limit is lost and only the hard limit is
 * left.
 *
 * Each function returns `null` when the value is fine, or a short English reason for the log when the
 * value must be rejected. Keys without a declared range (for example a plain [DoubleNonPreferenceKey])
 * are only checked for a finite number.
 */
object PreferenceValueCheck {

    /**
     * Same factor as `Constants.MMOLL_TO_MGDL` in `:core:data`. Copied, because `:core:keys` does not
     * depend on `:core:data`.
     */
    private const val MMOLL_TO_MGDL = 18.01559

    fun rejectReason(key: NonPreferenceKey, value: Double): String? {
        if (!value.isFinite()) return "not a finite number"
        return when (key) {
            is DoublePreferenceKey     -> outsideRange(value, key.min, key.max)
            is UnitDoublePreferenceKey -> unitDoubleRejectReason(key, value)
            else                       -> null
        }
    }

    fun rejectReason(key: NonPreferenceKey, value: Int): String? =
        if (key is IntPreferenceKey) outsideRange(value, key.min, key.max) else null

    fun rejectReason(key: NonPreferenceKey, value: Long): String? =
        if (key is LongPreferenceKey) outsideRange(value, key.min, key.max) else null

    /**
     * A unit double is stored as mg/dl, but older builds could store it in mmol/l, and the read side
     * (`ProfileUtil.valueInCurrentUnitsDetect`) still accepts both. So a value is fine when it is in
     * range as mg/dl, or when it is in range after a conversion from mmol/l.
     */
    private fun unitDoubleRejectReason(key: UnitDoublePreferenceKey, value: Double): String? {
        val min = key.minMgdl.toDouble()
        val max = key.maxMgdl.toDouble()
        if (value in min..max) return null
        if (value * MMOLL_TO_MGDL in min..max) return null
        return "outside the allowed range $min..$max mg/dl"
    }

    private fun <T : Comparable<T>> outsideRange(value: T, min: T, max: T): String? =
        if (value < min || value > max) "outside the allowed range $min..$max" else null
}
