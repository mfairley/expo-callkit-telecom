package expo.modules.callkittelecom.models

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Source/origin of a call session from the app/system perspective. */
enum class CallSessionOrigin(val value: String) {
    INCOMING("incoming"),
    OUTGOING_APP("outgoingApp"),
    OUTGOING_SYSTEM("outgoingSystem"),
}

/** Lifecycle status of a native call session. */
enum class CallSessionStatus(val value: String) {
    REQUESTING("requesting"),
    CONNECTING("connecting"),
    RINGING("ringing"),
    CONNECTED("connected"),
    ENDED("ended"),
}

/** Call-level options shared with JS and stored in session state. */
data class CallOptions(val hasVideo: Boolean)

/** Remote participant identity and optional contact/display details. */
data class CallParticipant(
    val id: String,
    val phoneNumber: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
) {
    /** Serializes participant data into the JS-facing event/session shape. */
    fun toMap(): Map<String, Any?> =
        mapOf(
                "id" to id,
                "phoneNumber" to phoneNumber,
                "email" to email,
                "displayName" to displayName,
                "avatarUrl" to avatarUrl,
            )
            .filterValues { it != null }

    companion object {
        /** Parses a JS/record dictionary into a strongly-typed participant model. */
        fun fromMap(map: Map<String, Any?>): CallParticipant =
            CallParticipant(
                id = map["id"] as? String ?: "",
                phoneNumber = map["phoneNumber"] as? String,
                email = map["email"] as? String,
                displayName = map["displayName"] as? String,
                avatarUrl = map["avatarUrl"] as? String,
            )
    }
}

/**
 * Validated incoming call event.
 *
 * Mirrors the TS `IncomingCallEvent` in `src/Calls.types.ts`.
 *
 * Two construction paths:
 * - [fromMap]: parses JS camelCase dictionaries (used by `reportIncomingCall`).
 * - [fromPayload]: parses a push payload that wraps the event under the top-level `incoming_call`
 *   key (used by VoIP push handling).
 */
data class IncomingCallEvent(
    val eventId: String,
    /** Server-assigned id for this call (distinct from the native UUID). */
    val serverCallId: String,
    val caller: Caller,
    val hasVideo: Boolean,
    /** Optional; defaults to now if absent. */
    val startedAt: Instant,
    /** App-defined extra fields forwarded verbatim from the push payload. */
    val metadata: Map<String, Any?>? = null,
) {
    /** Caller information embedded in incoming call events. */
    data class Caller(
        val id: String,
        val displayName: String? = null,
        val phoneNumber: String? = null,
        val email: String? = null,
        val avatarUrl: String? = null,
    ) {
        /** Serializes caller data into JS-facing payload shape. */
        fun toMap(): Map<String, Any?> =
            mapOf(
                    "id" to id,
                    "displayName" to displayName,
                    "phoneNumber" to phoneNumber,
                    "email" to email,
                    "avatarUrl" to avatarUrl,
                )
                .filterValues { it != null }

        companion object {
            /** Parses caller dictionaries (camelCase). */
            fun fromMap(map: Map<String, Any?>): Caller =
                Caller(
                    id = map["id"] as? String ?: "",
                    displayName = map["displayName"] as? String,
                    phoneNumber = map["phoneNumber"] as? String,
                    email = map["email"] as? String,
                    avatarUrl = map["avatarUrl"] as? String,
                )
        }
    }

    /** Serializes the event into the session payload shape expected by JS. */
    fun toMap(): Map<String, Any?> {
        val map =
            mutableMapOf<String, Any?>(
                "eventId" to eventId,
                "serverCallId" to serverCallId,
                "caller" to caller.toMap(),
                "hasVideo" to hasVideo,
                "startedAt" to DateTimeFormatter.ISO_INSTANT.format(startedAt),
            )
        if (metadata != null) {
            map["metadata"] = metadata
        }
        return map
    }

    companion object {
        /**
         * Parses and validates a JS-supplied event dictionary (camelCase keys).
         *
         * Required: `eventId`, `serverCallId`, `caller.id`.
         */
        fun fromMap(map: Map<String, Any?>): IncomingCallEvent {
            val callerMap = map["caller"] as? Map<String, Any?> ?: emptyMap()
            val startedAt =
                (map["startedAt"] as? String)?.let {
                    try {
                        Instant.parse(it)
                    } catch (_: Throwable) {
                        Instant.now()
                    }
                } ?: Instant.now()

            val eventId = map["eventId"] as? String ?: ""
            val serverCallId = map["serverCallId"] as? String ?: ""
            val callerId = callerMap["id"] as? String ?: ""

            require(eventId.isNotBlank()) { "IncomingCallEvent.eventId is required" }
            require(serverCallId.isNotBlank()) { "IncomingCallEvent.serverCallId is required" }
            require(callerId.isNotBlank()) { "IncomingCallEvent.caller.id is required" }

            @Suppress("UNCHECKED_CAST") val metadata = map["metadata"] as? Map<String, Any?>

            return IncomingCallEvent(
                eventId = eventId,
                serverCallId = serverCallId,
                caller = Caller.fromMap(callerMap),
                hasVideo = map["hasVideo"] as? Boolean ?: false,
                startedAt = startedAt,
                metadata = metadata,
            )
        }

        /**
         * Parses an `IncomingCallEvent` from a push payload.
         *
         * The payload MUST wrap the event under the top-level key `incoming_call`. There is no
         * fallback to a flat top-level shape. Inner keys are camelCase, matching the TS contract
         * and the example server.
         *
         * Returns `null` if the envelope is missing or required fields are absent.
         */
        @Suppress("UNCHECKED_CAST")
        fun fromPayload(payload: Map<String, Any?>): IncomingCallEvent? {
            val event = payload["incoming_call"] as? Map<String, Any?> ?: return null

            val eventId = event["eventId"] as? String ?: ""
            val serverCallId = event["serverCallId"] as? String ?: ""
            val callerMap = event["caller"] as? Map<String, Any?> ?: return null
            val callerId = callerMap["id"] as? String ?: ""

            if (eventId.isBlank() || serverCallId.isBlank() || callerId.isBlank()) {
                return null
            }

            val startedAt =
                (event["startedAt"] as? String)?.let {
                    try {
                        Instant.parse(it)
                    } catch (_: Throwable) {
                        null
                    }
                } ?: Instant.now()

            return IncomingCallEvent(
                eventId = eventId,
                serverCallId = serverCallId,
                caller = Caller.fromMap(callerMap),
                hasVideo = event["hasVideo"] as? Boolean ?: false,
                startedAt = startedAt,
                metadata = event["metadata"] as? Map<String, Any?>,
            )
        }
    }
}

/** In-memory representation of an active native call session. */
data class CallSession(
    val id: UUID,
    val options: CallOptions,
    val origin: CallSessionOrigin,
    val remoteParticipants: List<CallParticipant>,
    val incomingCallEvent: IncomingCallEvent? = null,
    val status: CallSessionStatus,
    val connectedAt: Instant? = null,
    val isMuted: Boolean = false,
    val isOnHold: Boolean = false,
    val dtmfDigits: String? = null,
) {
    /** Serializes session state into the exact JS-facing `CallSession` shape. */
    fun toMap(): Map<String, Any?> {
        val map =
            mutableMapOf<String, Any?>(
                "id" to id.toString(),
                "options" to mapOf("hasVideo" to options.hasVideo),
                "origin" to origin.value,
                "remoteParticipants" to remoteParticipants.map { it.toMap() },
                "status" to status.value,
                "isMuted" to isMuted,
                "isOnHold" to isOnHold,
            )

        if (dtmfDigits != null) {
            map["dtmfDigits"] = dtmfDigits
        }
        if (connectedAt != null) {
            map["connectedAt"] = DateTimeFormatter.ISO_INSTANT.format(connectedAt)
        }
        if (incomingCallEvent != null) {
            map["incomingCallEvent"] = incomingCallEvent.toMap()
        }

        return map
    }
}

/** Normalized call end reasons supported by the shared JS API. */
enum class CallEndedReason(val value: String) {
    FAILED("failed"),
    REMOTE_ENDED("remoteEnded"),
    UNANSWERED("unanswered"),
    ANSWERED_ELSEWHERE("answeredElsewhere"),
    DECLINED_ELSEWHERE("declinedElsewhere"),
    UNKNOWN("unknown");

    companion object {
        /** Safely maps a reason string to enum, defaulting to `UNKNOWN`. */
        fun fromValue(value: String): CallEndedReason =
            entries.firstOrNull { it.value == value } ?: UNKNOWN
    }
}
