package expo.modules.callkittelecom.store

import expo.modules.callkittelecom.events.CallEventEmitter
import expo.modules.callkittelecom.events.CallEvents
import expo.modules.callkittelecom.models.CallSession
import expo.modules.callkittelecom.models.CallSessionStatus
import java.util.LinkedHashMap
import java.util.UUID
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Thread-safe in-memory store for active call sessions.
 *
 * Emits session add/update/remove events whenever the backing state changes.
 */
object CallStore {
    private val lock = Any()
    private val sessions = LinkedHashMap<UUID, CallSession>()

    private data class SessionObserver(
        val callback: (CallSession) -> Unit,
        val filter: (CallSession) -> Boolean,
    )

    private val sessionObservers = mutableMapOf<UUID, MutableMap<UUID, SessionObserver>>()

    /** Returns the first active session (single-call model). */
    fun firstSession(): CallSession? = synchronized(lock) { sessions.values.firstOrNull() }

    /** Returns all sessions in insertion order. */
    fun allSessions(): List<CallSession> = synchronized(lock) { sessions.values.toList() }

    /** Returns a session by call UUID, or null if missing. */
    fun session(id: UUID): CallSession? = synchronized(lock) { sessions[id] }

    /** Adds a session and emits `onCallSessionAdded` if it did not already exist. */
    fun add(session: CallSession) {
        val sessionMap: Map<String, Any?>?
        synchronized(lock) {
            if (sessions.containsKey(session.id)) {
                sessionMap = null
            } else {
                sessions[session.id] = session
                sessionMap = session.toMap()
            }
        }
        if (sessionMap != null) {
            CallEventEmitter.send(CallEvents.CALL_SESSION_ADDED, mapOf("session" to sessionMap))
        }
    }

    /** Removes a session and emits `onCallSessionRemoved` when present. */
    fun remove(id: UUID) {
        val removed = synchronized(lock) { sessions.remove(id) != null }
        if (removed) {
            CallEventEmitter.send(CallEvents.CALL_SESSION_REMOVED, mapOf("id" to id.toString()))
        }
    }

    /** Removes all sessions and emits `onCallSessionRemoved` for each prior ID. */
    fun removeAll() {
        val ids =
            synchronized(lock) {
                val sessionIds = sessions.keys.toList()
                sessions.clear()
                sessionIds
            }
        ids.forEach { id ->
            CallEventEmitter.send(CallEvents.CALL_SESSION_REMOVED, mapOf("id" to id.toString()))
        }
    }

    /** Applies a copy-on-write transform and emits `onCallSessionUpdated` when changed. */
    fun update(id: UUID, transform: (CallSession) -> CallSession) {
        val sessionMap: Map<String, Any?>
        val matchingObservers: List<Pair<SessionObserver, CallSession>>

        synchronized(lock) {
            val current = sessions[id] ?: return
            val updated = transform(current)
            if (updated == current) return
            sessions[id] = updated
            sessionMap = updated.toMap()

            matchingObservers =
                sessionObservers[id]?.values?.mapNotNull { observer ->
                    if (observer.filter(updated)) observer to updated else null
                } ?: emptyList()
        }

        matchingObservers.forEach { (observer, session) -> observer.callback(session) }

        CallEventEmitter.send(CallEvents.CALL_SESSION_UPDATED, mapOf("session" to sessionMap))
    }

    /** Convenience status-only update helper. */
    fun updateStatus(id: UUID, status: CallSessionStatus) {
        update(id) { it.copy(status = status) }
    }

    /** Convenience mute-state update helper. */
    fun updateMuted(id: UUID, isMuted: Boolean) {
        update(id) { it.copy(isMuted = isMuted) }
    }

    /** Convenience hold-state update helper. */
    fun updateHeld(id: UUID, isOnHold: Boolean) {
        update(id) { it.copy(isOnHold = isOnHold) }
    }

    /**
     * Returns a [Flow] that emits session updates matching the optional filter.
     *
     * Immediately emits the current session if it exists and matches the filter. Useful for
     * observing status transitions on a specific call.
     */
    fun sessionUpdates(
        callId: UUID,
        filter: (CallSession) -> Boolean = { true },
    ): Flow<CallSession> = callbackFlow {
        val observerId = UUID.randomUUID()

        synchronized(lock) {
            val current = sessions[callId]
            if (current != null && filter(current)) {
                trySend(current)
            }

            val observers = sessionObservers.getOrPut(callId) { mutableMapOf() }
            observers[observerId] =
                SessionObserver(callback = { session -> trySend(session) }, filter = filter)
        }

        awaitClose {
            synchronized(lock) {
                sessionObservers[callId]?.remove(observerId)
                if (sessionObservers[callId]?.isEmpty() == true) {
                    sessionObservers.remove(callId)
                }
            }
        }
    }
}
