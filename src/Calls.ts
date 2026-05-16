/**
 * @module Calls
 *
 * This module provides APIs for managing VoIP calls with native system integration.
 * Functions are organized into three categories:
 *
 * ## Requests (Imperative)
 * Functions that initiate actions from the app. These request the system to perform
 * an operation on behalf of the user:
 * - {@link startOutgoingCall} - Start a new outgoing call
 * - {@link answerCall} - Answer an incoming call
 * - {@link endCall} - End or decline an active call
 * - {@link setMuted} - Mute/unmute the call
 * - {@link setHeld} - Hold/unhold the call
 * - {@link playDTMF} - Play DTMF tones
 *
 * ## Reporters
 * Functions that report state changes to the system. Use these to inform the system
 * about events that occurred outside of its control (e.g., from your backend or
 * media connection):
 * - {@link reportIncomingCall} - Report a new incoming call (e.g., from push notification)
 * - {@link reportOutgoingCallConnected} - Report that an outgoing call's media is connected
 * - {@link reportCallEnded} - Report that a call ended externally (e.g., remote hangup)
 * - {@link reportVideo} - Report video state changes
 *
 * ## Fulfillers
 * Functions that complete pending system requests. When the system requests an action
 * (via event listeners), your app must perform the action and then call the corresponding
 * fulfiller to confirm completion:
 * - {@link fulfillIncomingCallConnected} - Confirm that incoming call media is connected
 *
 * ## Typical Flow
 *
 * **Outgoing Call:**
 * 1. Call {@link startOutgoingCall} to initiate
 * 2. Listen for {@link addCallStartedListener} to know when to connect media
 * 3. Connect your media (e.g., WebRTC)
 * 4. Call {@link reportOutgoingCallConnected} when media is ready
 *
 * **Incoming Call:**
 * 1. Receive push notification with call data
 * 2. Call {@link reportIncomingCall} to show the incoming call UI
 * 3. Listen for {@link addCallAnsweredListener} to know when user answered
 * 4. Connect your media (e.g., WebRTC)
 * 5. Call {@link fulfillIncomingCallConnected} when media is ready
 *
 * **Ending a Call:**
 * - If user ends: Call {@link endCall} and clean up media
 * - If remote ends: Clean up media, then call {@link reportCallEnded}
 */

import type { EventSubscription } from "expo-modules-core";
import { Platform } from "react-native";

import type {
  AudioRouteChangedEvent,
  AudioSession,
  AudioSessionActivatedEvent,
  AudioSessionDeactivatedEvent,
  CallAnsweredEvent,
  CallEndedEvent,
  CallEndedReason,
  CallIntentReceivedEvent,
  CallOptions,
  CallParticipant,
  CallReportedEnded,
  CallSession,
  CallSessionAddedEvent,
  CallSessionRemovedEvent,
  CallSessionUpdatedEvent,
  CaptureSession,
  DTMFEvent,
  IncomingCallEvent,
  IncomingCallReportedEvent,
  OutgoingCallStartedEvent,
  SetHeldActionEvent,
  SetMutedActionEvent,
  VideoChangedEvent,
  VoIPPushToken,
  VoIPPushTokenUpdatedEvent,
} from "./Calls.types";
import type { PushTokenType } from "./Calls.types";
import ExpoCallKitTelecomModule from "./ExpoCallKitTelecomModule";

// ============================================================================
// Call Session
// ============================================================================

/**
 * Gets the currently active call session, if any.
 *
 * @returns The active call session, or `null` if no call is in progress.
 *
 * @example
 * ```typescript
 * const session = await getActiveCallSession();
 * if (session) {
 *   console.log('Active call with:', session.remoteParticipants[0]?.displayName);
 * }
 * ```
 *
 * @category Sessions
 */
export async function getActiveCallSession(): Promise<CallSession | null> {
  const session = await ExpoCallKitTelecomModule.getActiveCallSession();
  if (session) {
  }
  return session;
}

/**
 * Subscribes to call session added events.
 *
 * Fired when a new call session is created, either from an outgoing call request
 * or an incoming call report.
 *
 * @param listener - Callback invoked when a session is added.
 * @returns A subscription that can be removed by calling `.remove()`.
 *
 * @example
 * ```typescript
 * const subscription = addCallSessionAddedListener((event) => {
 *   console.log('New call session:', event.session.id);
 * });
 *
 * // Later, to unsubscribe:
 * subscription.remove();
 * ```
 *
 * @category Sessions
 */
export function addCallSessionAddedListener(
  listener: (event: CallSessionAddedEvent) => void,
): EventSubscription {
  return ExpoCallKitTelecomModule.addListener(
    "onCallSessionAdded",
    (event: CallSessionAddedEvent) => {
      listener(event);
    },
  );
}

/**
 * Subscribes to call session updated events.
 *
 * Fired when an existing call session's state changes (e.g., status, mute state).
 *
 * @param listener - Callback invoked when a session is updated.
 * @returns A subscription that can be removed by calling `.remove()`.
 *
 * @category Sessions
 */
export function addCallSessionUpdatedListener(
  listener: (event: CallSessionUpdatedEvent) => void,
): EventSubscription {
  return ExpoCallKitTelecomModule.addListener(
    "onCallSessionUpdated",
    (event: CallSessionUpdatedEvent) => {
      listener(event);
    },
  );
}

/**
 * Subscribes to call session removed events.
 *
 * Fired when a call session is removed after the call has ended and been cleaned up.
 *
 * @param listener - Callback invoked when a session is removed.
 * @returns A subscription that can be removed by calling `.remove()`.
 *
 * @category Sessions
 */
export function addCallSessionRemovedListener(
  listener: (event: CallSessionRemovedEvent) => void,
): EventSubscription {
  return ExpoCallKitTelecomModule.addListener("onCallSessionRemoved", listener);
}

// ============================================================================
// Audio Session
// ============================================================================

/**
 * Gets the current audio session state.
 *
 * Returns information about the audio session including whether it's active,
 * the current category/mode, and the audio route (speaker, earpiece, etc.).
 *
 * @returns The current audio session state.
 *
 * @category Audio
 */
export function getAudioSession(): AudioSession {
  return ExpoCallKitTelecomModule.getAudioSessionState();
}

/**
 * Gets the current capture session state.
 *
 * Returns information about the capture session including camera permission status.
 *
 * @returns The current capture session state.
 *
 * @category Capture
 */
export function getCaptureSession(): CaptureSession {
  return ExpoCallKitTelecomModule.getCaptureSessionState();
}

/**
 * Sets the RTC audio session configuration (iOS only).
 *
 * This sets up WebRTC's RTCAudioSession default configuration and enables manual
 * audio management. On Android this is a no-op — audio configuration is handled
 * by {@link prepareAudioSessionForCall}.
 *
 * @param hasVideo - Whether to configure for video calls (uses speaker by default)
 *   or audio-only calls (uses earpiece by default).
 *
 * @category Audio
 */
export function setRTCAudioSessionConfiguration(hasVideo: boolean): void {
  if (Platform.OS === "ios") {
    ExpoCallKitTelecomModule.setRTCAudioSessionConfiguration(hasVideo);
  }
}

/**
 * Prepares the audio session for an upcoming call.
 *
 * This snapshots the current audio configuration (for later restoration) and
 * pre-configures the audio session for the call. Called automatically when
 * reporting/starting a call, but can be called manually for early preparation.
 *
 * @param hasVideo - Whether to configure for video calls (uses speaker by default)
 *   or audio-only calls (uses earpiece by default).
 *
 * @category Audio
 */
export function prepareAudioSessionForCall(hasVideo: boolean): void {
  ExpoCallKitTelecomModule.prepareAudioSessionForCall(hasVideo);
}

/**
 * Restores the audio session to its pre-call configuration.
 *
 * Call this if a call fails to start after prepareAudioSessionForCall was called,
 * or to manually restore the audio session. This is called automatically when
 * the audio session is deactivated after a call ends.
 *
 * @category Audio
 */
export function restoreAudioSession(): void {
  ExpoCallKitTelecomModule.restoreAudioSession();
}

/**
 * Sets the audio session port override.
 *
 * Use this to route audio to the speaker instead of the earpiece, or vice versa.
 *
 * @param enabled - If `true`, routes audio to the speaker. If `false`, uses
 *   the default route (typically earpiece for voice calls).
 *
 * @category Audio
 */
export function setAudioSessionPortOverride(enabled: boolean): void {
  ExpoCallKitTelecomModule.setAudioSessionPortOverride(enabled);
}

/**
 * Subscribes to audio session activated events.
 *
 * Fired when the audio session is activated for a call. This is when your
 * app gains exclusive access to audio hardware.
 *
 * @param listener - Callback invoked when audio session activates.
 * @returns A subscription that can be removed by calling `.remove()`.
 *
 * @category Audio Events
 */
export function addAudioSessionActivatedListener(
  listener: (event: AudioSessionActivatedEvent) => void,
): EventSubscription {
  return ExpoCallKitTelecomModule.addListener("onAudioSessionActivated", listener);
}

/**
 * Subscribes to audio session deactivated events.
 *
 * Fired when the audio session is deactivated after a call ends.
 *
 * @param listener - Callback invoked when audio session deactivates.
 * @returns A subscription that can be removed by calling `.remove()`.
 *
 * @category Audio Events
 */
export function addAudioSessionDeactivatedListener(
  listener: (event: AudioSessionDeactivatedEvent) => void,
): EventSubscription {
  return ExpoCallKitTelecomModule.addListener("onAudioSessionDeactivated", listener);
}

/**
 * Subscribes to audio route changed events.
 *
 * Fired when the audio route changes (e.g., user connects Bluetooth headphones,
 * toggles speaker mode).
 *
 * @param listener - Callback invoked when audio route changes.
 * @returns A subscription that can be removed by calling `.remove()`.
 *
 * @category Audio Events
 */
export function addAudioRouteChangedListener(
  listener: (event: AudioRouteChangedEvent) => void,
): EventSubscription {
  return ExpoCallKitTelecomModule.addListener("onAudioRouteChanged", listener);
}

// ============================================================================
// Call Intent Event Listeners
// ============================================================================

/**
 * Subscribes to call intent received events.
 *
 * Fired when the user initiates a call from outside the app, such as tapping
 * a contact in the iOS Recents list or via Siri. The event contains the handle
 * (phone number/email) and whether video is requested.
 *
 * The app should resolve the handle to a known recipient and call
 * {@link startOutgoingCall} to fulfill the intent.
 *
 * @param listener - Callback invoked when a call intent is received.
 * @returns A subscription that can be removed by calling `.remove()`.
 *
 * @category Call Events
 */
export function addCallIntentReceivedListener(
  listener: (event: CallIntentReceivedEvent) => void,
): EventSubscription {
  return ExpoCallKitTelecomModule.addListener("onCallIntentReceived", listener);
}

// ============================================================================
// Start Outgoing Call
// ============================================================================

/**
 * Starts an outgoing call to the specified recipient.
 *
 * This requests the system to initiate a call. The system will display the
 * appropriate call UI and emit an {@link OutgoingCallStartedEvent} when you should
 * begin connecting your media.
 *
 * @param recipient - The participant to call.
 * @param options - Call configuration options (e.g., video enabled).
 * @returns The unique identifier for this call session.
 *
 * @example
 * ```typescript
 * const callId = await startOutgoingCall(
 *   { id: 'user-123', displayName: 'John Doe' },
 *   { hasVideo: true }
 * );
 * ```
 *
 * @category Requests
 */
export async function startOutgoingCall(
  recipient: CallParticipant,
  options: CallOptions,
): Promise<string> {
  return await ExpoCallKitTelecomModule.startOutgoingCall(recipient, options);
}

/**
 * Subscribes to outgoing call started events.
 *
 * Fired when an outgoing call (initiated via {@link startOutgoingCall}) has
 * been accepted by the system. You should provision your media connection
 * and begin connecting.
 *
 * @param listener - Callback invoked when an outgoing call starts.
 * @returns A subscription that can be removed by calling `.remove()`.
 *
 * @category Call Events
 */
export function addOutgoingCallStartedListener(
  listener: (event: OutgoingCallStartedEvent) => void,
): EventSubscription {
  return ExpoCallKitTelecomModule.addListener("onOutgoingCallStarted", listener);
}

// ============================================================================
// Report Incoming Call
// ============================================================================

/**
 * Reports an incoming call to the system.
 *
 * Call this when you receive a push notification or other signal indicating
 * an incoming call. The system will display the incoming call UI.
 *
 * @param event - The incoming call event containing caller information.
 *
 * @example
 * ```typescript
 * await reportIncomingCall({
 *   callId: '550e8400-e29b-41d4-a716-446655440000',
 *   caller: {
 *     id: 'user-456',
 *     displayName: 'Jane Smith',
 *     phoneNumber: '+1234567890',
 *   },
 *   hasVideo: false,
 *   startedAt: new Date(),
 * });
 * ```
 *
 * @category Reporters
 */
export async function reportIncomingCall(
  event: IncomingCallEvent,
): Promise<void> {
  await ExpoCallKitTelecomModule.reportIncomingCall(event);
}

/**
 * Subscribes to incoming call reported events.
 *
 * Fired after an incoming call has been successfully reported to the system
 * and the call session has been added to the store. Use this to set up
 * early subscriptions (e.g., call signaling) before the call is answered.
 *
 * @param listener - Callback invoked when an incoming call is reported.
 * @returns A subscription that can be removed by calling `.remove()`.
 *
 * @category Call Events
 */
export function addIncomingCallReportedListener(
  listener: (event: IncomingCallReportedEvent) => void,
): EventSubscription {
  return ExpoCallKitTelecomModule.addListener("onIncomingCallReported", listener);
}

// ============================================================================
// Answer Call
// ============================================================================

/**
 * Answers an incoming call.
 *
 * Use this when the user taps an answer button in your app's custom UI.
 * The system will emit a {@link CallAnsweredEvent} to confirm the answer.
 *
 * @param id - The call session ID to answer.
 *
 * @category Requests
 */
export async function answerCall(id: string): Promise<void> {
  await ExpoCallKitTelecomModule.answerCall(id);
}

/**
 * Subscribes to call answered events.
 *
 * Fired when the user answers an incoming call (either from the system UI or
 * via {@link answerCall}). You should begin connecting your media.
 *
 * @param listener - Callback invoked when a call is answered.
 * @returns A subscription that can be removed by calling `.remove()`.
 *
 * @category Call Events
 */
export function addCallAnsweredListener(
  listener: (event: CallAnsweredEvent) => void,
): EventSubscription {
  return ExpoCallKitTelecomModule.addListener("onCallAnswered", listener);
}

// ============================================================================
// Fulfill Incoming Call
// ============================================================================

/**
 * Fulfills an incoming call by confirming the media connection is established.
 *
 * Call this after the user answers an incoming call and your media connection
 * (e.g., WebRTC) is fully connected and ready for audio/video.
 *
 * @param requestId - The request ID from the CallAnsweredEvent.
 *
 * @category Fulfillers
 */
export async function fulfillIncomingCallConnected(
  requestId: string,
): Promise<void> {
  await ExpoCallKitTelecomModule.fulfillIncomingCallAnswered(requestId);
}

/**
 * Fails a pending incoming call connection request.
 *
 * Call this when the answer flow fails before media is connected
 * (e.g., API error). On iOS, causes CXAnswerCallAction to fail, which
 * triggers CallKit to end the call via CXEndCallAction. On Android,
 * ends the call via {@link reportCallEnded} which also cancels any
 * pending fulfill request.
 *
 * @param id - The call session ID.
 * @param requestId - The request ID from the CallAnsweredEvent.
 *
 * @category Fulfillers
 */
export async function failIncomingCallConnected(
  id: string,
  requestId: string,
): Promise<void> {
  if (Platform.OS === "ios") {
    await ExpoCallKitTelecomModule.failIncomingCallConnected(requestId);
  } else {
    await ExpoCallKitTelecomModule.reportCallEnded(id, "failed");
  }
}

// ============================================================================
// Report Outgoing Call Connected
// ============================================================================

/**
 * Reports that an outgoing call's media connection is established.
 *
 * Call this after starting an outgoing call and your media connection
 * (e.g., WebRTC) is fully connected and the remote party has answered.
 *
 * @param id - The call session ID.
 *
 * @category Reporters
 */
export async function reportOutgoingCallConnected(id: string): Promise<void> {
  await ExpoCallKitTelecomModule.reportOutgoingCallConnected(id);
}

// ============================================================================
// End Call
// ============================================================================

/**
 * Ends an active call.
 *
 * Requests the system to end the call. The system will emit a {@link CallEndedEvent}
 * to notify that the call has ended. Clean up your media connection when you receive
 * this event.
 *
 * @param id - The call session ID to end.
 *
 * @category Requests
 */
export async function endCall(id: string): Promise<void> {
  await ExpoCallKitTelecomModule.endCall(id);
}

/**
 * Subscribes to call ended events.
 *
 * Fired when a call has ended (e.g., user pressed end button).
 * Clean up your media connection when you receive this event.
 *
 * @param listener - Callback invoked when a call ends.
 * @returns A subscription that can be removed by calling `.remove()`.
 *
 * @category Call Events
 */
export function addCallEndedListener(
  listener: (event: CallEndedEvent) => void,
): EventSubscription {
  return ExpoCallKitTelecomModule.addListener("onCallEnded", listener);
}

/**
 * Reports that a call has ended for an external reason.
 *
 * Use this when a call ends due to reasons outside the local user's control,
 * such as: remote party hung up, network failure, call declined elsewhere, etc.
 *
 * @param id - The call session ID.
 * @param reason - The reason the call ended.
 *
 * @example
 * ```typescript
 * // Remote party hung up
 * await reportCallEnded(callId, 'remoteEnded');
 *
 * // Call failed due to network error
 * await reportCallEnded(callId, 'failed');
 * ```
 *
 * @category Reporters
 */
export async function reportCallEnded(
  id: string,
  reason: CallEndedReason,
): Promise<void> {
  await ExpoCallKitTelecomModule.reportCallEnded(id, reason);
}

/**
 * Subscribes to reported call ended events.
 *
 * Fired after {@link reportCallEnded} is called, confirming the system has
 * been notified of the externally-ended call.
 *
 * @param listener - Callback invoked when a call end is reported.
 * @returns A subscription that can be removed by calling `.remove()`.
 *
 * @category Call Events
 */
export function addReportedCallEndedListener(
  listener: (event: CallReportedEnded) => void,
): EventSubscription {
  return ExpoCallKitTelecomModule.addListener("onCallReportedEnded", listener);
}

// ============================================================================
// Mute Support
// ============================================================================

/**
 * Changes the mute state of a call.
 *
 * The system will emit a {@link SetMutedActionEvent}. Apply the mute state to
 * your media connection when you receive this event.
 *
 * @param id - The call session ID.
 * @param muted - Whether the microphone should be muted.
 *
 * @category Requests
 */
export async function setMuted(id: string, muted: boolean): Promise<void> {
  await ExpoCallKitTelecomModule.setMuted(id, muted);
}

/**
 * Subscribes to set muted action events.
 *
 * Fired when the system requests to set the mute state (e.g., user pressed mute button).
 * Apply the change to your media connection when you receive this event.
 *
 * @param listener - Callback invoked when set muted action is requested.
 * @returns A subscription that can be removed by calling `.remove()`.
 *
 * @category Call Events
 */
export function addSetMutedActionListener(
  listener: (event: SetMutedActionEvent) => void,
): EventSubscription {
  return ExpoCallKitTelecomModule.addListener("onSetMutedAction", listener);
}

// ============================================================================
// Video Support
// ============================================================================

/**
 * Reports a video state change for a call.
 *
 * Use this to inform the system when video is enabled or disabled.
 *
 * @param id - The call session ID.
 * @param enabled - Whether video is enabled.
 *
 * @category Reporters
 */
export async function reportVideo(id: string, enabled: boolean): Promise<void> {
  await ExpoCallKitTelecomModule.reportVideo(id, enabled);
}

/**
 * Subscribes to video state change events.
 *
 * Fired when the video state changes for a call.
 *
 * @param listener - Callback invoked when video state changes.
 * @returns A subscription that can be removed by calling `.remove()`.
 *
 * @category Call Events
 */
export function addVideoChangedListener(
  listener: (event: VideoChangedEvent) => void,
): EventSubscription {
  return ExpoCallKitTelecomModule.addListener("onVideoChanged", listener);
}

// ============================================================================
// Hold Support
// ============================================================================

/**
 * Changes the hold state of a call.
 *
 * The system will emit a {@link SetHeldActionEvent}. Apply the hold state to
 * your media connection when you receive this event.
 *
 * @param id - The call session ID.
 * @param onHold - Whether the call should be on hold.
 *
 * @category Requests
 */
export async function setHeld(id: string, onHold: boolean): Promise<void> {
  await ExpoCallKitTelecomModule.setHeld(id, onHold);
}

/**
 * Subscribes to set held action events.
 *
 * Fired when the system requests to set the hold state. Apply the change to
 * your media connection when you receive this event.
 *
 * @param listener - Callback invoked when set held action is requested.
 * @returns A subscription that can be removed by calling `.remove()`.
 *
 * @category Call Events
 */
export function addSetHeldActionListener(
  listener: (event: SetHeldActionEvent) => void,
): EventSubscription {
  return ExpoCallKitTelecomModule.addListener("onSetHeldAction", listener);
}

// ============================================================================
// DTMF Support
// ============================================================================

/**
 * Plays DTMF tones during a call.
 *
 * The system will emit a {@link DTMFEvent}. Send the tones through your media
 * connection when you receive this event.
 *
 * @param id - The call session ID.
 * @param digits - The DTMF digits to play (0-9, *, #).
 *
 * @category Requests
 */
export async function playDTMF(id: string, digits: string): Promise<void> {
  await ExpoCallKitTelecomModule.playDTMF(id, digits);
}

/**
 * Subscribes to DTMF events.
 *
 * Fired when DTMF tones should be played. Send the tones through your media
 * connection when you receive this event.
 *
 * @param listener - Callback invoked when DTMF tones should be played.
 * @returns A subscription that can be removed by calling `.remove()`.
 *
 * @category Call Events
 */
export function addDTMFListener(
  listener: (event: DTMFEvent) => void,
): EventSubscription {
  return ExpoCallKitTelecomModule.addListener("onDTMF", listener);
}

// ============================================================================
// VoIP Push
// ============================================================================

/**
 * Registers for VoIP push notifications.
 *
 * Call this early in your app lifecycle to receive VoIP push notifications
 * for incoming calls. Once registered, the device token will be available
 * via {@link getVoIPPushToken} and token updates will be emitted via
 * {@link addVoIPPushTokenUpdatedListener}.
 *
 * @example
 * ```typescript
 * // Register early in app initialization
 * registerVoIPPush();
 *
 * // Listen for token updates
 * addVoIPPushTokenUpdatedListener((event) => {
 *   if (event.token) {
 *     // Send token to your backend
 *     sendTokenToBackend(event.token);
 *   }
 * });
 * ```
 *
 * @category VoIP Push
 */
export function registerVoIPPush(): void {
  ExpoCallKitTelecomModule.registerVoIPPush();
}

/**
 * Gets the current VoIP push token and its type.
 *
 * The token should be sent to your backend along with the token type
 * so the server knows how to deliver incoming call pushes.
 *
 * @returns The VoIP push token bundled with its type, or null if not yet registered.
 *
 * @example
 * ```typescript
 * const voip = getVoIPPushToken();
 * if (voip) {
 *   await sendTokenToBackend(voip.token, voip.type);
 * }
 * ```
 *
 * @category VoIP Push
 */
export function getVoIPPushToken(): VoIPPushToken | null {
  const result = ExpoCallKitTelecomModule.getVoIPPushToken();
  if (!result.token) return null;
  return {
    token: result.token,
    type: result.type as PushTokenType,
  };
}

/**
 * Subscribes to VoIP token updated events.
 *
 * Fired when the VoIP push token is received or updated after calling
 * {@link registerVoIPPush}. Also fired if the token is invalidated (with
 * `token` being `undefined`).
 *
 * @param listener - Callback invoked when the VoIP token updates.
 * @returns A subscription that can be removed by calling `.remove()`.
 *
 * @example
 * ```typescript
 * const subscription = addVoIPPushTokenUpdatedListener((event) => {
 *   if (event.token) {
 *     console.log('New VoIP token:', event.token);
 *     sendTokenToBackend(event.token);
 *   } else {
 *     console.log('VoIP token invalidated');
 *   }
 * });
 * ```
 *
 * @category VoIP Push
 */
export function addVoIPPushTokenUpdatedListener(
  listener: (event: VoIPPushTokenUpdatedEvent) => void,
): EventSubscription {
  return ExpoCallKitTelecomModule.addListener("onVoIPPushTokenUpdated", listener);
}
