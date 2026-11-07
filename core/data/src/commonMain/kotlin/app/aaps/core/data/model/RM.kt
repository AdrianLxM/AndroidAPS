package app.aaps.core.data.model

import app.aaps.core.data.time.systemUtcOffsetAt

data class RM(
    override var id: Long = 0,
    override var version: Int = 0,
    override var dateCreated: Long = -1,
    override var isValid: Boolean = true,
    override var referenceId: Long? = null,
    override var ids: IDs = IDs(),
    override var timestamp: Long,
    var utcOffset: Long = systemUtcOffsetAt(timestamp),
    /** Current running mode. */
    var mode: Mode,
    /** Planned duration in milliseconds */
    var duration: Long,
    /**
     * true if forced automatically in loop plugin,
     * false if initiated by user
     */
    var autoForced: Boolean = false,
    /** List of reasons for automated mode change */
    var reasons: String? = null
) : HasIDs, TimeStamped {

    fun contentEqualsTo(other: RM): Boolean =
        timestamp == other.timestamp &&
            utcOffset == other.utcOffset &&
            mode == other.mode &&
            duration == other.duration &&
            autoForced == other.autoForced &&
            isValid == other.isValid

    fun onlyNsIdAdded(previous: RM): Boolean =
        previous.id != id &&
            contentEqualsTo(previous) &&
            previous.ids.nightscoutId == null &&
            ids.nightscoutId != null

    /**
     * True for a record in the temporary layer (duration > 0). This includes the two "endless"
     * values, see [isEndless]. The "active at" database queries use the same rule, so do not change it.
     */
    fun isTemporary() = duration > 0

    /**
     * True when this mode has no planned end: a permanent mode (duration 0), or a mode stored with
     * one of the two "endless" durations, [ENDLESS_DURATION] or [PUMP_SUSPEND_DURATION].
     */
    val isEndless: Boolean
        get() = duration <= 0L || duration >= PUMP_SUSPEND_DURATION

    /**
     * Planned end of this mode in milliseconds, or [Long.MAX_VALUE] when it [isEndless].
     * Never overflows (see [endOf]), so use this instead of `timestamp + duration`.
     */
    val end: Long
        get() = if (isEndless) Long.MAX_VALUE else endOf(timestamp, duration)

    enum class Mode {
        OPEN_LOOP,
        CLOSED_LOOP,
        CLOSED_LOOP_LGS,

        // Can be permanent (duration=0) or temporary; NS sync layer substitutes a long
        // wire duration so the offline marker still renders.
        DISABLED_LOOP,

        // Temporary only
        SUPER_BOLUS,
        DISCONNECTED_PUMP,
        SUSPENDED_BY_PUMP,
        SUSPENDED_BY_USER,
        SUSPENDED_BY_DST,

        /**
         * Not a real mode but only option to cancel temporary mode
         * (i.e. RECONNECT_PUMP, RESUME_LOOP, CANCEL_SUPERBOLUS)
         */
        RESUME
        ;

        fun isClosedLoopOrLgs() = this == CLOSED_LOOP || this == CLOSED_LOOP_LGS
        fun isLoopRunning() = this == OPEN_LOOP || this == CLOSED_LOOP || this == CLOSED_LOOP_LGS

        /**
         * True when the loop algorithm should not run / not dispatch new dosing decisions.
         * Includes SUPER_BOLUS (the bolus is delivered by the wizard, basal is forced to 0)
         * and all explicit suspends. **Does not** mean "manual bolus blocked" — for that
         * use `PumpCommandGate.check` with `PumpCommandGate.CommandKind.BOLUS`.
         */
        fun pausesLoopExecution() = this == DISCONNECTED_PUMP || this == SUSPENDED_BY_PUMP || this == SUSPENDED_BY_USER || this == SUSPENDED_BY_DST || this == SUPER_BOLUS
        fun mustBeTemporary() = this == DISCONNECTED_PUMP || this == SUSPENDED_BY_PUMP || this == SUSPENDED_BY_USER || this == SUSPENDED_BY_DST || this == SUPER_BOLUS

        companion object {

            fun fromString(reason: String?) = Mode.entries.firstOrNull { it.name == reason } ?: DEFAULT_MODE
        }
    }

    companion object {

        val DEFAULT_MODE = Mode.DISABLED_LOOP

        /**
         * Duration of a mode that the loop forced because of a constraint (DISABLED_LOOP, OPEN_LOOP,
         * CLOSED_LOOP_LGS). It has no end; the loop ends it when the constraint is gone.
         */
        const val ENDLESS_DURATION: Long = Long.MAX_VALUE

        /**
         * Duration of SUSPENDED_BY_PUMP: Int.MAX_VALUE minutes (about 4000 years) in milliseconds.
         * It has no end; the loop ends it when the pump runs again. It is a second "endless" value
         * next to [ENDLESS_DURATION] because it is already stored in databases and sent to Nightscout,
         * where [Long.MAX_VALUE] does not survive JSON (JavaScript numbers are exact only up to 2^53).
         * Every duration of this size or more counts as endless, see [isEndless].
         */
        const val PUMP_SUSPEND_DURATION: Long = 2_147_483_647L * 60_000L
    }
}