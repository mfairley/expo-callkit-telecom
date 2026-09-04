// Default timeout values in seconds
export const DEFAULT_INCOMING_CALL_TIMEOUT = 45;
export const DEFAULT_OUTGOING_CALL_TIMEOUT = 60;
export const DEFAULT_FULFILL_ANSWER_CALL_TIMEOUT = 30;

// Whether completed calls appear in the phone's call history (Recents)
export const DEFAULT_INCLUDES_CALLS_IN_RECENTS = true;

/**
 * Action for the package-internal call-event broadcast (Android). The
 * `androidEventReceiver` plugin prop registers the manifest receiver for it.
 */
export const ANDROID_CALL_EVENT_ACTION =
  "expo.modules.callkittelecom.ACTION_CALL_EVENT";
