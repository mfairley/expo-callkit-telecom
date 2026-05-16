package expo.modules.callkittelecom.events

/**
 * Canonical event names emitted by the Android native call layer.
 *
 * These values must stay aligned with the TypeScript listeners in `Calls.ts`.
 */
object CallEvents {
    /** Emitted when a new native call session is created. */
    const val CALL_SESSION_ADDED = "onCallSessionAdded"

    /** Emitted when an existing native call session changes state. */
    const val CALL_SESSION_UPDATED = "onCallSessionUpdated"

    /** Emitted when a native call session is removed. */
    const val CALL_SESSION_REMOVED = "onCallSessionRemoved"

    /** Emitted when call audio has been activated for one or more calls. */
    const val AUDIO_SESSION_ACTIVATED = "onAudioSessionActivated"

    /** Emitted when call audio has been deactivated after call teardown. */
    const val AUDIO_SESSION_DEACTIVATED = "onAudioSessionDeactivated"

    /** Emitted when the current input/output route changes. */
    const val AUDIO_ROUTE_CHANGED = "onAudioRouteChanged"

    /** Emitted after an incoming call is successfully handed to Telecom. */
    const val INCOMING_CALL_REPORTED = "onIncomingCallReported"

    /** Emitted when an outgoing call has started and JS should connect media. */
    const val OUTGOING_CALL_STARTED = "onOutgoingCallStarted"

    /** Emitted when an incoming call is answered and media setup should begin. */
    const val CALL_ANSWERED = "onCallAnswered"

    /** Emitted when the local user/system ends a call. */
    const val CALL_ENDED = "onCallEnded"

    /** Emitted when the app reports an externally-ended call reason. */
    const val CALL_REPORTED_ENDED = "onCallReportedEnded"

    /** Emitted when mute state is requested or changed. */
    const val SET_MUTED_ACTION = "onSetMutedAction"

    /** Emitted when video enabled state changes. */
    const val VIDEO_CHANGED = "onVideoChanged"

    /** Emitted when hold state is requested or changed. */
    const val SET_HELD_ACTION = "onSetHeldAction"

    /** Emitted when DTMF digits are requested. */
    const val DTMF = "onDTMF"

    /** Reserved for call intent integration. */
    const val CALL_INTENT_RECEIVED = "onCallIntentReceived"

    /** Emitted when the FCM push token is refreshed. */
    const val VOIP_PUSH_TOKEN_UPDATED = "onVoIPPushTokenUpdated"
}
