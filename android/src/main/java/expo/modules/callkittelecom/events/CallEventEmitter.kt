package expo.modules.callkittelecom.events

import expo.modules.callkittelecom.utils.CallKitTelecomLog
import java.time.Instant
import java.time.format.DateTimeFormatter

private data class QueuedEvent(val body: Map<String, Any?>, val timestamp: Instant)

/**
 * Event bridge between Android native call state and the Expo JS module.
 *
 * Behavior:
 * - Tracks which individual events are being observed by JS
 * - Queues events that arrive before observers mount
 * - Flushes queued events with `{ meta: { flushed: true } }`
 *
 * All mutable state is guarded by [lock] for thread safety.
 */
object CallEventEmitter {
    private const val TAG = "ExpoCallKitTelecom.Emitter"

    private val lock = Any()
    private val observingEvents = mutableSetOf<String>()
    private val eventQueues = mutableMapOf<String, MutableList<QueuedEvent>>()
    private val queueLimits = mutableMapOf<String, Int?>()

    var defaultQueueLimit: Int? = 0

    @Volatile private var sender: ((String, Map<String, Any?>) -> Unit)? = null

    /** Sets or clears the active event sender provided by the Expo module. */
    fun setSender(eventSender: ((String, Map<String, Any?>) -> Unit)?) {
        sender = eventSender
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
     */
    fun send(eventName: String, body: Map<String, Any?>) {
        val timestamp = Instant.now()
        val senderRef = sender
        val isObserving = synchronized(lock) { observingEvents.contains(eventName) }

        if (senderRef != null && isObserving) {
            CallKitTelecomLog.d(TAG) { "Sending event to JS - name: $eventName" }
            senderRef(eventName, buildEventBody(body, flushed = false, timestamp = timestamp))
            return
        }
        queueEvent(eventName, body, timestamp)
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

    /** Queues an event and enforces per-event queue limits (drop oldest first). */
    private fun queueEvent(name: String, body: Map<String, Any?>, timestamp: Instant) {
        synchronized(lock) {
            val limit = queueLimits[name] ?: defaultQueueLimit
            if (limit == 0) {
                CallKitTelecomLog.d(TAG) { "Dropping event (queueing disabled) - name: $name" }
                return
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
        }
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
