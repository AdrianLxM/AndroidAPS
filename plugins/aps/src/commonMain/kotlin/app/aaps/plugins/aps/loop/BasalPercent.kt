package app.aaps.plugins.aps.loop

/**
 * The [rate] (U/h) as a percent of the profile [basal] (U/h).
 *
 * Returns null when [basal] is not above 0, for example a 0 U/h block in the profile. The plain
 * division would give Infinity (toInt() is then Int.MAX_VALUE) or NaN (toInt() is then 0), and
 * neither is a real percent.
 */
internal fun basalPercent(rate: Double, basal: Double): Int? =
    if (basal > 0.0) (rate / basal * 100).toInt() else null
