package app.aaps.implementation.queue.commands

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.aps.SmbIntervalGate
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

class CommandSMBBolus(
    private val aapsLogger: AAPSLogger,
    private val rh: TextResolver,
    private val dateUtil: DateUtil,
    private val activePlugin: ActivePlugin,
    private val persistenceLayer: PersistenceLayer,
    private val preferences: Preferences,
    private val bolusProgressData: BolusProgressData,
    override val pumpEnactResultProvider: () -> PumpEnactResult,
    private val detailedBolusInfo: DetailedBolusInfo,
    private val bolusGeneration: Long,
) : Command() {

    override val commandType: Command.CommandType = Command.CommandType.SMB_BOLUS

    override suspend fun execute(): PumpEnactResult {
        val r: PumpEnactResult
        val lastBolusTime = persistenceLayer.getNewestBolus()?.timestamp ?: 0L
        aapsLogger.debug(LTag.PUMPQUEUE, "Last bolus: $lastBolusTime ${dateUtil.dateAndTimeAndSecondsString(lastBolusTime)}")
        if (loopPausedNow()) {
            // The queue checks the running mode only when the SMB is added. The user can disconnect the pump or
            // suspend the loop while the SMB waits for the pump to connect. Same rule as `LoopPlugin.applySMBRequest`.
            aapsLogger.debug(LTag.PUMPQUEUE, "SMB bolus canceled. Running mode pauses the loop now")
            r = pumpEnactResultProvider().enacted(false).success(false).comment(InterfacesStrings.pumpsuspended)
        } else if (SmbIntervalGate.tooSoon(lastBolusTime, preferences.get(IntKey.ApsMaxSmbFrequency), dateUtil.now())) {
            aapsLogger.debug(LTag.APS, "SMB requested but still in ${preferences.get(IntKey.ApsMaxSmbFrequency)} min interval")
            r = pumpEnactResultProvider().enacted(false).success(false).comment("SMB requested but still in ${preferences.get(IntKey.ApsMaxSmbFrequency)} min interval")
        } else if (detailedBolusInfo.deliverAtTheLatest != 0L && detailedBolusInfo.deliverAtTheLatest + T.mins(1).msecs() > Clock.System.now().toEpochMilliseconds()) {
            r = activePlugin.activePump.deliverTreatment(detailedBolusInfo)
        } else {
            r = pumpEnactResultProvider().enacted(false).success(false).comment("SMB request too old")
            aapsLogger.debug(LTag.PUMPQUEUE, "SMB bolus canceled. deliverAt: " + dateUtil.dateAndTimeString(detailedBolusInfo.deliverAtTheLatest))
        }
        aapsLogger.debug(LTag.PUMPQUEUE, "Result success: ${r.success} enacted: ${r.enacted}")
        // Generation-scoped: never wipe a newer bolus that was enqueued behind this SMB (see BolusProgressData.clear).
        bolusProgressData.clear(bolusGeneration)
        return r
    }

    /**
     * True when the running mode does not allow loop dosing now. If the mode cannot be read, the SMB is not
     * given: unlike a user bolus, a dropped SMB is safe, because the next loop run calculates again.
     */
    private suspend fun loopPausedNow(): Boolean =
        try {
            persistenceLayer.getRunningModeActiveAt(dateUtil.now()).mode.pausesLoopExecution()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            aapsLogger.warn(LTag.PUMPQUEUE, "SMB: failed to read running mode, SMB not given: ${e.message}")
            true
        }

    override fun status(): String = rh.gs(CoreUiStrings.smb_bolus_u, detailedBolusInfo.insulin)

    override fun log(): String = "SMB BOLUS ${rh.gs(InterfacesStrings.format_insulin_units, detailedBolusInfo.insulin)}"

    override fun cancel(comment: TextRef, success: Boolean, cancelled: Boolean) {
        super.cancel(comment, success, cancelled)
        bolusProgressData.clear(bolusGeneration)
    }
}
