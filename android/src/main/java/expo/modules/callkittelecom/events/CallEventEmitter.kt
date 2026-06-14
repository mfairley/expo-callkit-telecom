package expo.modules.callkittelecom.events

import android.content.Context
import android.content.Intent
import expo.modules.callkittelecom.utils.CallKitTelecomLog
import java.time.Instant
import java.time.format.DateTimeFormatter
import org.json.JSONObject

private data class QueuedEvent(val body: Map<String, Any?>, val timestamp: Instant)

/**
 * Event bridge between Android native call state and the Expo JS module.
 *
 * Behavior:
 * - Tracks which individual events are being observed by JS
 * - Queues events that arrive before observers mount
 * - Flushes queued events with `{ meta: { flushed: true } }`
 * - Broadcasts events that can't reach JS at all (dropped, not queued) when a broadcast context is
 *   set, so a JS-less process can still react (see [setBroadcastContext])
 *
 * All mutable state is guarded by [lock] for thread safety.
 */
object CallEventEmitter {
    private const val TAG = "ExpoCallKitTelecom.Emitter"

    /**
     * Package-internal broadcast action for call events that couldn't reach a live JS observer.
     * Apps receive it with a manifest receiver, registered via the `androidEventReceiver`
     * config-plugin prop.
     */
    const val ACTION_CALL_EVENT = "expo.modules.callkittelecom.ACTION_CALL_EVENT"

    private val lock = Any()
    private val observingEvents = mutableSetOf<String>()
    private val eventQueues = mutableMapOf<String, MutableList<QueuedEvent>>()
    private val queueLimits = mutableMapOf<String, Int?>()

    var defaultQueueLimit: Int? = 0

    @Volatile private var sender: ((String, Map<String, Any?>) -> Unit)? = null

    @Volatile private var broadcastContext: Context? = null

    /** Sets or clears the active event sender provided by the Expo module. */
    fun setSender(eventSender: ((String, Map<String, Any?>) -> Unit)?) {
        sender = eventSender
    }

    /**
     * Enables (or disables, with `null`) the broadcast path by providing an application context.
     *
     * When set, any event that [send] *drops* — i.e. one that can't reach a live JS observer and
     * isn't queued for cold-start replay (queue limit 0) — is emitted as a package-internal
     * [ACTION_CALL_EVENT] broadcast, so a process started by a push or Telecom with no React
     * context can still react (e.g. notify a backend of a killed-app decline). Queued events are
     * not broadcast: they flush to JS once observed, so the queue already covers them.
     *
     * Broadcasting sits next to the queue — the emitter's existing handling for events JS can't
     * receive yet — rather than behind an injected hook, since the actual consumer (the app's
     * manifest receiver) is already decoupled by the OS and there is nothing to inject.
     */
    fun setBroadcastContext(context: Context?) {
        broadcastContext = context?.applicationContext
    }

    /**
     * Configures queue size for a specific event.
     *
     * `null` means unlimited queueing, `0` disables queueing.
     */
    fun setQueueLimit(eventName: String, limit: Int?) {
        synchronized(lock) { queueLimits[eventName] = limit }
    }

    /**
     * Sends an event to JS if it is currently observed, or queues it otherwise.
     *
     * All delivered events are augmented with a `meta` object containing timestamp and flush
     * status.
     *
     * @return true when the event was delivered to a live JS observer, false when it was queued (or
     *   dropped by the event's queue limit). When the event was *dropped* (it will never reach JS)
     *   and a broadcast context is set (see [setBroadcastContext]), it is also broadcast so native
     *   code can deliver out-of-band — e.g. the app process was started by a push or Telecom with
     *   no React context. Events that were queued are not broadcast: they flush to JS once
     *   observed, so broadcasting them too would double-deliver.
     */
    fun send(eventName: String, body: Map<String, Any?>): Boolean {
        val timestamp = Instant.now()
        val senderRef = sender
        val isObserving = synchronized(lock) { observingEvents.contains(eventName) }

        if (senderRef != null && isObserving) {
            CallKitTelecomLog.d(TAG) { "Sending event to JS - name: $eventName" }
            senderRef(eventName, buildEventBody(body, flushed = false, timestamp = timestamp))
            return true
        }
        val queued = queueEvent(eventName, body, timestamp)
        if (!queued) {
            broadcastContext?.let { context ->
                broadcastUndelivered(
                    context,
                    eventName,
                    buildEventBody(body, flushed = false, timestamp = timestamp),
                )
            }
        }
        return false
    }

    /**
     * Broadcasts an undelivered event so a JS-less process can still react.
     *
     * Fires a package-internal ([Intent.setPackage]) [ACTION_CALL_EVENT] broadcast carrying the
     * event name and the JS-shaped body (the same one JS would have received) as a JSON `payload`.
     * Receivers discriminate on the `eventName` extra. Failures are swallowed (warn-logged): a
     * dropped broadcast must never break event emission.
     */
    private fun broadcastUndelivered(context: Context, eventName: String, body: Map<String, Any?>) {
        try {
            val intent =
                Intent(ACTION_CALL_EVENT)
                    .setPackage(context.packageName)
                    .putExtra("eventName", eventName)
                    .putExtra("payload", JSONObject(body).toString())
            context.sendBroadcast(intent)
            CallKitTelecomLog.d(TAG) {
                "Broadcast undelivered event (no live JS observer) - name: $eventName"
            }
        } catch (e: Exception) {
            CallKitTelecomLog.w(TAG) { "Event broadcast failed - name: $eventName: ${e.message}" }
        }
    }

    /** Marks an event as observed and flushes any pending queue for that event. */
    fun startObserving(eventName: String) {
        val queueCount: Int
        synchronized(lock) {
            queueCount = eventQueues[eventName]?.size ?: 0
            observingEvents.add(eventName)
        }
        CallKitTelecomLog.d(TAG) {
            "Start observing - event: $eventName, queuedEvents: $queueCount"
        }
        flushQueue(eventName)
    }

    /** Marks an event as no longer observed by JS. */
    fun stopObserving(eventName: String) {
        CallKitTelecomLog.d(TAG) { "Stop observing - event: $eventName" }
        synchronized(lock) { observingEvents.remove(eventName) }
    }

    /** Adds native event metadata used by TypeScript event types. */
    private fun buildEventBody(
        body: Map<String, Any?>,
        flushed: Boolean,
        timestamp: Instant,
    ): Map<String, Any?> {
        val result = body.toMutableMap()
        result["meta"] =
            mapOf(
                "flushed" to flushed,
                "timestamp" to DateTimeFormatter.ISO_INSTANT.format(timestamp),
            )
        return result
    }

    /**
     * Queues an event and enforces per-event queue limits (drop oldest first).
     *
     * @return true when the event was queued (and will flush to JS once observed), false when
     *   queueing is disabled for it (limit 0) so it was dropped and will never reach JS.
     */
    private fun queueEvent(name: String, body: Map<String, Any?>, timestamp: Instant): Boolean =
        synchronized(lock) {
            val limit = queueLimits[name] ?: defaultQueueLimit
            if (limit == 0) {
                CallKitTelecomLog.d(TAG) { "Dropping event (queueing disabled) - name: $name" }
                return@synchronized false
            }

            val queue = eventQueues.getOrPut(name) { mutableListOf() }
            queue += QueuedEvent(body = body, timestamp = timestamp)

            if (limit != null && queue.size > limit) {
                val dropCount = queue.size - limit
                repeat(dropCount) { queue.removeAt(0) }
                CallKitTelecomLog.d(TAG) {
                    "Queueing event (dropped $dropCount old) - name: $name, queueSize: ${queue.size}"
                }
            } else {
                CallKitTelecomLog.d(TAG) {
                    "Queueing event (JS not listening) - name: $name, queueSize: ${queue.size}"
                }
            }
            true
        }

    /** Flushes all queued events for a single event name. */
    private fun flushQueue(eventName: String) {
        val senderRef = sender ?: return
        val queue = synchronized(lock) { eventQueues.remove(eventName) } ?: return
        if (queue.isEmpty()) return

        CallKitTelecomLog.d(TAG) {
            "Flushing event queue - event: $eventName, count: ${queue.size}"
        }
        queue.forEach { event ->
            senderRef(
                eventName,
                buildEventBody(event.body, flushed = true, timestamp = event.timestamp),
            )
        }
    }
}
