/**
 * Type of VoIP push token reported by `getVoIPPushToken`.
 *
 * - `"APNS_VOIP"` — Apple Push Notification service VoIP channel (iOS)
 * - `"FCM"` — Firebase Cloud Messaging (Android)
 *
 * @category VoIP Push
 */
export type PushTokenType = "APNS_VOIP" | "FCM";

// ============================================================================
// Native event infrastructure
// ============================================================================

/**
 * Metadata attached to every native event.
 *
 * @category Core
 */
export interface NativeEventMeta {
  /** Whether the event was flushed from queue (true) or sent in real-time (false) */
  flushed: boolean;
  /** ISO8601 timestamp of when the event was created */
  timestamp: string;
}

/**
 * Base shape extended by every native event — carries a {@link NativeEventMeta} envelope.
 *
 * @category Core
 */
export interface NativeEvent {
  meta: NativeEventMeta;
}

// ============================================================================
// Call sessions
// ============================================================================

/**
 * Active call as tracked by the module.
 *
 * Represents one in-flight call. Mirrors the OS-side `CXCall` (iOS) /
 * `CallControlScope` (Android) plus app-level state (origin, participants,
 * incoming-call payload, mute/hold).
 *
 * @category Sessions
 */
export interface CallSession {
  id: string;
  options: CallOptions;
  origin: CallSessionOrigin;
  remoteParticipants: CallParticipant[];
  incomingCallEvent?: IncomingCallEvent;
  status: CallSessionStatus;
  connectedAt?: string;
  isMuted: boolean;
  isOnHold: boolean;
  dtmfDigits?: string;
}

/**
 * Per-call options set at session start.
 *
 * @category Sessions
 */
export interface CallOptions {
  hasVideo: boolean;
}

/**
 * Where a call session originated.
 *
 * - `incoming` — Reported from a VoIP push (or directly via `reportIncomingCall`).
 * - `outgoingApp` — Started by your app via `startOutgoingCall`.
 * - `outgoingSystem` — Started by the OS via a call intent (Recents, Siri).
 *
 * @category Sessions
 */
export type CallSessionOrigin = "incoming" | "outgoingApp" | "outgoingSystem";

/**
 * Identity for a remote party on a call.
 *
 * @category Sessions
 */
export interface CallParticipant {
  /** Opaque, stable app identifier for this participant. */
  id: string;
  /** Display name. */
  displayName?: string;
  /** Avatar URL. */
  avatarUrl?: string;
  /** Phone number in E.164 (e.g. "+14155551234"). When present on iOS, the
   *  CallKit handle is set to this number, enabling Recents and Siri. */
  phoneNumber?: string;
  /** Email address. */
  email?: string;
}

/**
 * Call session status representing the lifecycle of a call.
 *
 * Outgoing call flow: requesting → connecting → connected → ended
 * Incoming call flow: ringing → connecting → connected → ended
 *
 * - `requesting` — Outgoing only. The call request has been submitted to
 *   CallKit/Telecom and is awaiting system acceptance.
 * - `ringing` — Incoming only. The call has been reported to CallKit/Telecom
 *   and the user sees the native incoming call UI or notification.
 * - `connecting` — Both directions. For outgoing calls, the system accepted the
 *   call and the dialtone is playing while waiting for the remote party to answer.
 *   For incoming calls, the user answered and media is being established.
 * - `connected` — Both directions. Media is flowing and the call is active.
 * - `ended` — Both directions. Transient state during teardown before the
 *   session is removed from the store.
 *
 * @category Sessions
 */
export type CallSessionStatus =
  | "requesting"
  | "connecting"
  | "ringing"
  | "connected"
  | "ended";

/**
 * Fired when a new {@link CallSession} is created (outgoing request or incoming report).
 *
 * @category Sessions
 */
export interface CallSessionAddedEvent extends NativeEvent {
  session: CallSession;
}

/**
 * Fired when an existing {@link CallSession}'s state changes (status, mute, hold, etc.).
 *
 * @category Sessions
 */
export interface CallSessionUpdatedEvent extends NativeEvent {
  session: CallSession;
}

/**
 * Fired when a {@link CallSession} is removed after the call has ended and been cleaned up.
 *
 * @category Sessions
 */
export interface CallSessionRemovedEvent extends NativeEvent {
  id: string;
}

// ============================================================================
// Permissions
// ============================================================================

/**
 * Permission status for microphone and camera, reported on {@link AudioSession}
 * and {@link CaptureSession}.
 *
 * @category Permissions
 */
export type PermissionStatus =
  | "granted"
  | "denied"
  | "undetermined"
  | "restricted"
  | "unknown";

// ============================================================================
// Audio session
// ============================================================================

/**
 * Snapshot of the current audio session, including activation state, route,
 * and (on iOS) the WebRTC `RTCAudioSession` coordination flags.
 *
 * @category Audio
 */
export interface AudioSession {
  isActive: boolean;
  /** iOS only: whether the WebRTC RTCAudioSession is active. */
  rtcSessionIsActive?: boolean;
  /** iOS only: whether the AVAudioSession is active. */
  avSessionIsActive?: boolean;
  /** iOS only: whether the RTCAudioSession audio track is enabled. */
  isAudioEnabled?: boolean;
  /** iOS only: whether manual audio mode is enabled on RTCAudioSession. */
  useManualAudio?: boolean;
  isOtherAudioPlaying: boolean;
  category: string;
  mode: string;
  /** iOS only: AVAudioSession category options. */
  categoryOptions?: string[];
  sampleRate: number;
  ioBufferDuration: number;
  inputNumberOfChannels: number;
  outputNumberOfChannels: number;
  microphonePermission: PermissionStatus;
  currentRoute: AudioRoute;
  /** Available audio output devices. Populated on Android; undefined on iOS. */
  availableRoutes?: AudioPort[];
}

/**
 * Currently-selected audio inputs and outputs.
 *
 * @category Audio
 */
export interface AudioRoute {
  inputs: AudioPort[];
  outputs: AudioPort[];
}

/**
 * A single audio input or output (earpiece, speaker, headphones, Bluetooth device, etc.).
 *
 * @category Audio
 */
export interface AudioPort {
  portType: AudioOutputPortType;
  portName: string;
  uid: string;
}

/**
 * Cross-platform audio output port type identifiers.
 * Both iOS and Android map their native audio device types to these shared values.
 *
 * @category Audio
 */
export type AudioOutputPortType =
  | "builtInReceiver" // Earpiece
  | "builtInSpeaker" // Speaker
  | "headphones" // Wired headphones
  | "bluetoothA2DP" // Bluetooth A2DP audio
  | "bluetoothLE" // Bluetooth Low Energy audio
  | "bluetoothHFP" // Bluetooth Hands-Free Profile
  | "airPlay" // AirPlay
  | "hdmi" // HDMI output
  | "carAudio" // CarPlay
  | "usbAudio" // USB audio
  | "lineOut" // Line out
  | (string & {}); // Allow other unknown port types

/**
 * Brief summary of one call associated with an audio-session activation event.
 *
 * @category Audio Events
 */
export interface AudioSessionCallInfo {
  id: string;
  status: CallSessionStatus;
}

/**
 * Fired when the system activates the audio session for a call.
 *
 * @category Audio Events
 */
export interface AudioSessionActivatedEvent extends NativeEvent {
  calls: AudioSessionCallInfo[];
}

/**
 * Fired when the system deactivates the audio session after a call.
 *
 * @category Audio Events
 */
export interface AudioSessionDeactivatedEvent extends NativeEvent {
  calls: AudioSessionCallInfo[];
}

/**
 * Fired when the active audio route changes (e.g. AirPods connected, speaker toggled).
 *
 * @category Audio Events
 */
export interface AudioRouteChangedEvent extends NativeEvent {
  currentRoute: AudioRoute;
  /** Available audio output devices. Populated on Android; undefined on iOS. */
  availableRoutes?: AudioPort[];
}

// ============================================================================
// Capture session (camera)
// ============================================================================

/**
 * Snapshot of camera-related state, including permission and (on iOS 16+)
 * multitasking-camera availability.
 *
 * @category Capture
 */
export interface CaptureSession {
  cameraPermission: PermissionStatus;
  /** Whether the device supports multitasking camera access (iOS 16+). */
  isMultitaskingCameraAccessSupported?: boolean;
}

// ============================================================================
// Call action events
// ============================================================================

/**
 * Base shape for any event carrying a {@link CallSession.id}.
 *
 * @category Call Events
 */
export interface CallActionEvent extends NativeEvent {
  id: string;
}

/**
 * Fired after `startOutgoingCall`, once the OS has accepted the call request.
 *
 * @category Call Events
 */
export interface OutgoingCallStartedEvent extends CallActionEvent {}

/**
 * Payload describing one incoming call.
 *
 * Delivered both inside a VoIP push (parsed natively by the module) and on
 * {@link CallSession.incomingCallEvent} for any incoming-origin session.
 *
 * @category VoIP Push
 */
export interface IncomingCallEvent {
  /** Unique event identifier (UUID). Used for dedup. */
  eventId: string;
  /** Your backend's id for this call. Distinct from {@link CallSession.id},
   *  which is the OS-assigned native call UUID. Use this id to talk to your
   *  server about the call (e.g. POST /calls/:serverCallId/answer). */
  serverCallId: string;
  /** True for video calls, false for audio. */
  hasVideo: boolean;
  /** RFC 3339 timestamp of when the call was placed. Optional; defaults to now. */
  startedAt?: string;
  /** Caller identity and addressing. */
  caller: CallParticipant;
  /**
   * App-defined extra fields, forwarded verbatim from the push payload.
   *
   * The library treats this as opaque — put whatever your app needs here
   * (chatId, tenantId, room name, etc.). Cast to your own type at the
   * read site.
   */
  metadata?: Record<string, unknown>;
}

/**
 * Fired after `reportIncomingCall`, once the OS has accepted the incoming-call report.
 *
 * @category Call Events
 */
export interface IncomingCallReportedEvent extends CallActionEvent {}

/**
 * Fired when the user answers an incoming call from the system UI.
 *
 * @category Call Events
 */
export interface CallAnsweredEvent extends CallActionEvent {
  requestId: string;
}

/**
 * Fired when the user ends a call from the system UI, or the OS ends the call for any reason.
 *
 * @category Call Events
 */
export interface CallEndedEvent extends CallActionEvent {}

/**
 * Reason a call was ended, reported on {@link CallReportedEnded}.
 *
 * @category Call Events
 */
export type CallEndedReason =
  | "failed"
  | "remoteEnded"
  | "unanswered"
  | "answeredElsewhere"
  | "declinedElsewhere"
  | "unknown";

/**
 * Fired when the app calls `reportCallEnded` to inform the OS the call has ended
 * externally (e.g. remote hang-up).
 *
 * @category Call Events
 */
export interface CallReportedEnded extends CallActionEvent {
  reason: CallEndedReason;
}

/**
 * Fired when the system requests a mute-state change (e.g. user pressed the
 * mute button in the CallKit UI). Apply the change to your media connection.
 *
 * @category Call Events
 */
export interface SetMutedActionEvent extends CallActionEvent {
  isMuted: boolean;
}

/**
 * Fired when video state changes on a call.
 *
 * @category Call Events
 */
export interface VideoChangedEvent extends CallActionEvent {
  hasVideo: boolean;
}

/**
 * Fired when the system requests a hold-state change. Apply the change to your media connection.
 *
 * @category Call Events
 */
export interface SetHeldActionEvent extends CallActionEvent {
  isOnHold: boolean;
}

/**
 * Fired when the system requests DTMF tones be played on the call.
 *
 * @category Call Events
 */
export interface DTMFEvent extends CallActionEvent {
  digits: string;
}

// ============================================================================
// Call intents (iOS Recents, Siri "call X")
// ============================================================================

/**
 * Kind of handle attached to a call intent (Recents tap, Siri).
 *
 * @category Call Events
 */
export type CallIntentHandleType = "phoneNumber" | "email" | "unknown";

/**
 * Fired when the OS routes a "start call" intent to the app — e.g. the user
 * tapped a Recents entry or said "call Jane" to Siri.
 *
 * @category Call Events
 */
export interface CallIntentReceivedEvent extends NativeEvent {
  handle: string;
  handleType: CallIntentHandleType;
  hasVideo: boolean;
}

// ============================================================================
// VoIP push
// ============================================================================

/**
 * A VoIP push token bundled with its transport type.
 *
 * @category VoIP Push
 */
export interface VoIPPushToken {
  /** The VoIP push token string. */
  token: string;
  /** The type of token this platform provides. */
  type: PushTokenType;
}

/**
 * Fired when the VoIP push token is received, refreshed, or invalidated.
 *
 * @category VoIP Push
 */
export interface VoIPPushTokenUpdatedEvent extends NativeEvent {
  /** The VoIP push token string, or undefined if invalidated */
  token?: string;
  /** The type of VoIP push token (e.g. "APNS_VOIP", "FCM"). */
  type: PushTokenType;
}
