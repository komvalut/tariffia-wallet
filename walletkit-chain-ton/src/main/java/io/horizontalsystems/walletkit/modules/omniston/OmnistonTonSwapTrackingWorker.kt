package io.horizontalsystems.walletkit.modules.omniston

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.horizontalsystems.walletkit.core.App
import io.horizontalsystems.walletkit.core.managers.OmnistonRepository
import io.horizontalsystems.walletkit.core.managers.OmnistonSwapEvent
import io.horizontalsystems.walletkit.entities.SwapRecord
import io.horizontalsystems.walletkit.modules.multiswap.history.SwapRecordManager
import io.horizontalsystems.walletkit.modules.multiswap.history.SwapStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.TimeUnit

class OmnistonTonSwapTrackingWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val recordId = inputData.getInt(KEY_RECORD_ID, INVALID_RECORD_ID)
        if (recordId == INVALID_RECORD_ID) return Result.failure()

        val records = App.swapRecordManager
        val record = records.getById(recordId) ?: return Result.success()
        if (record.providerId != PROVIDER_ID || record.trackingHandle == null) return Result.success()
        if (record.status in TERMINAL_STATUSES) return Result.success()

        val tracking = runCatching { JSONObject(record.trackingHandle) }.getOrElse {
            Log.e(TAG, "Invalid tracking data for swap record $recordId", it)
            return Result.failure()
        }
        val quoteId = tracking.optString("quoteId")
        val traderAddress = tracking.optString("traderAddress")
        val outgoingTxQuery = tracking.optString("outgoingTxQuery")
        val outputDecimals = tracking.optInt("outputDecimals", 0)
        if (quoteId.isBlank() || traderAddress.isBlank() || outgoingTxQuery.isBlank()) return Result.failure()

        return try {
            withTimeout(TRACKING_WINDOW_MS) {
                OmnistonRepository().trackSwap(quoteId, traderAddress, outgoingTxQuery).collect { event ->
                    if (updateRecord(records, record, outputDecimals, event)) throw TrackingFinished
                }
            }
            Result.retry()
        } catch (_: TrackingFinishedException) {
            Result.success()
        } catch (_: TimeoutCancellationException) {
            Result.retry()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.e(TAG, "Omniston tracking failed for swap record $recordId", e)
            Result.retry()
        }
    }

    private fun updateRecord(
        recordManager: SwapRecordManager,
        record: SwapRecord,
        outputDecimals: Int,
        event: OmnistonSwapEvent,
    ): Boolean {
        val currentRecord = recordManager.getById(record.id) ?: record
        if (event is OmnistonSwapEvent.AwaitingTransfer) {
            updateStatus(recordManager, currentRecord, SwapStatus.Depositing)
            return false
        }

        event as OmnistonSwapEvent.Progress
        event.outgoingTxHash
            ?.takeIf { it.isNotBlank() && it != currentRecord.transactionHash }
            ?.let { recordManager.updateTransactionHash(currentRecord.id, it) }
        event.outboundTxHash
            ?.takeIf { it.isNotBlank() && it != currentRecord.outboundTransactionHash }
            ?.let { recordManager.updateOutboundTransactionHash(currentRecord.id, it) }

        val status = mapStatus(event.status, event.routeCount) ?: return false
        val terminal = status == SwapStatus.Completed || status == SwapStatus.Failed
        val actualOutput = event.actualOutputUnits?.takeIf { it.signum() > 0 }
        if (terminal && status == SwapStatus.Completed && actualOutput != null && outputDecimals in 0..255) {
            val amountOut = BigDecimal(actualOutput, outputDecimals).stripTrailingZeros().toPlainString()
            recordManager.updateStatusAndAmountOut(currentRecord.id, status, amountOut, null)
        } else {
            updateStatus(recordManager, currentRecord, status)
        }
        return terminal
    }

    private fun updateStatus(recordManager: SwapRecordManager, record: SwapRecord, status: SwapStatus) {
        if (record.status != status.name) recordManager.updateStatus(record.id, status, null)
    }

    private fun mapStatus(status: String, routeCount: Int): SwapStatus? = when (status) {
        "TRADE_STATUS_FULLY_FILLED", "TRADE_STATUS_PARTIALLY_FILLED" -> SwapStatus.Completed
        "TRADE_STATUS_FAILED", "TRADE_STATUS_CANCELLED" -> SwapStatus.Failed
        "TRADE_STATUS_IN_PROGRESS" -> if (routeCount > 0) SwapStatus.Swapping else SwapStatus.Depositing
        else -> null
    }

    private class TrackingFinishedException : Exception()

    companion object {
        const val KEY_RECORD_ID = "omniston_ton_swap_record_id"
        const val INVALID_RECORD_ID = -1
        const val PROVIDER_ID = "omniston_ton"
        const val TAG = "OmnistonTonTracking"
        const val TRACKING_WINDOW_MS = 8 * 60 * 1000L
        private val TrackingFinished = TrackingFinishedException()
        private val TERMINAL_STATUSES = setOf(
            SwapStatus.Completed.name,
            SwapStatus.Refunded.name,
            SwapStatus.Failed.name,
            SwapStatus.Expired.name,
        )

        fun workName(recordId: Int) = "omniston-ton-swap-$recordId"

        fun enqueue(context: Context, recordId: Int) {
            val request = OneTimeWorkRequestBuilder<OmnistonTonSwapTrackingWorker>()
                .setInputData(workDataOf(KEY_RECORD_ID to recordId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RETRY_BACKOFF_MS, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                workName(recordId),
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun enqueuePending(context: Context, records: List<SwapRecord>) {
            records.asSequence()
                .filter { it.providerId == PROVIDER_ID && it.trackingHandle != null }
                .forEach { enqueue(context, it.id) }
        }

        const val RETRY_BACKOFF_MS = 30_000L
    }
}
