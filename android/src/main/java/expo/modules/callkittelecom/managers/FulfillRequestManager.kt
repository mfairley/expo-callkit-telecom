package expo.modules.callkittelecom.managers

import expo.modules.callkittelecom.utils.CallKitTelecomLog
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pending fulfill request metadata for answered incoming calls.
 *
 * `requestId` is sent to JS and must be fulfilled once media is connected.
 */
data class FulfillRequest(val requestId: UUID, val callId: UUID)

/** Result of a fulfill request. */
sealed interface FulfillResult {
    /** The request was successfully fulfilled, includes the associated call ID. */
    data class Fulfilled(val callId: UUID) : FulfillResult

    /** The request timed out before being fulfilled. */
    data object TimedOut : FulfillResult
}

/**
 * Tracks pending answer fulfill requests with timeout behavior.
 *
 * Semantics:
 * - create request on answer
 * - resolve via fulfill(requestId)
 * - auto-timeout if JS never fulfills
 *
 * All mutable state is guarded by [lock] for thread safety.
 */
object FulfillRequestManager {
    private const val TAG = "ExpoCallKitTelecom.Fulfill"

    private val lock = Any()
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val requests = mutableMapOf<UUID, UUID>()
    private val timeoutJobs = mutableMapOf<UUID, Job>()

    /**
     * Creates a pending fulfill request for a call.
     *
     * @param callId Call UUID associated with this request.
     * @param timeoutMs Maximum wait time before automatic timeout.
     * @param onTimeout Callback invoked with the call UUID when request expires.
     */
    fun createRequest(callId: UUID, timeoutMs: Long, onTimeout: (UUID) -> Unit): FulfillRequest {
        val requestId = UUID.randomUUID()

        val job =
            scope.launch {
                delay(timeoutMs)
                val removedCallId =
                    synchronized(lock) {
                        timeoutJobs.remove(requestId)
                        requests.remove(requestId)
                    }
                if (removedCallId != null) {
                    CallKitTelecomLog.d(TAG) {
                        "Fulfill request timed out - requestId: $requestId, callId: $removedCallId"
                    }
                    onTimeout(removedCallId)
                }
            }

        synchronized(lock) {
            requests[requestId] = callId
            timeoutJobs[requestId] = job
        }

        CallKitTelecomLog.d(TAG) {
            "Created fulfill request - requestId: $requestId, callId: $callId, timeout: ${timeoutMs}ms"
        }
        return FulfillRequest(requestId = requestId, callId = callId)
    }

    /**
     * Fulfills a request by ID.
     *
     * @return associated call UUID when request exists, else null (already timed out/handled).
     */
    fun fulfill(requestId: UUID): UUID? {
        val job: Job?
        val callId: UUID?
        synchronized(lock) {
            job = timeoutJobs.remove(requestId)
            callId = requests.remove(requestId)
        }
        job?.cancel()

        if (callId != null) {
            CallKitTelecomLog.d(TAG) {
                "Fulfill request succeeded - requestId: $requestId, callId: $callId"
            }
        } else {
            CallKitTelecomLog.d(TAG) {
                "Fulfill request not found (likely timed out) - requestId: $requestId"
            }
        }
        return callId
    }

    /**
     * Cancels a pending request by request ID without fulfilling it.
     *
     * Use when the request should be aborted (e.g., call ended before connection).
     */
    fun cancel(requestId: UUID) {
        val job: Job?
        synchronized(lock) {
            job = timeoutJobs.remove(requestId)
            requests.remove(requestId)
        }
        job?.cancel()
        if (job != null) {
            CallKitTelecomLog.d(TAG) { "Fulfill request cancelled - requestId: $requestId" }
        }
    }

    /**
     * Cancels any pending fulfill request associated with a specific call.
     *
     * Used when a call ends before JS fulfills the answer request. This is a convenience for the
     * call-end path where only the call ID is available rather than the request ID.
     */
    fun cancelForCall(callId: UUID) {
        val entriesToCancel: List<Pair<UUID, Job?>>
        synchronized(lock) {
            val requestIds = requests.entries.filter { it.value == callId }.map { it.key }
            entriesToCancel =
                requestIds.map { reqId ->
                    val job = timeoutJobs.remove(reqId)
                    requests.remove(reqId)
                    reqId to job
                }
        }
        entriesToCancel.forEach { (reqId, job) ->
            job?.cancel()
            CallKitTelecomLog.d(TAG) {
                "Fulfill request cancelled for call - requestId: $reqId, callId: $callId"
            }
        }
    }
}
