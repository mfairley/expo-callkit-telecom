/**
 * Type of VoIP push token reported by `getVoIPPushToken`.
 *
 * - `"APNS_VOIP"` — Apple Push Notification service VoIP channel (iOS)
 * - `"FCM"` — Firebase Cloud Messaging (Android)
 */
export type PushTokenType = "APNS_VOIP" | "FCM";

// Native Event Metadata

/** Metadata attached to all native events */
export interface NativeEventMeta {
  /** Whether the event was flushed from queue (true) or sent in real-time (false) */
  flushed: boolean;
  /** ISO8601 timestamp of when the event was created */
  timestamp: string;
}

/** Base type for all native events with metadata */
export interface NativeEvent {
  meta: NativeEventMeta;
}

// Call Session
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

export interface CallOptions {
  hasVideo: boolean;
}

export type CallSessionOrigin = "incoming" | "outgoingApp" | "outgoingSystem";

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
 */
export type CallSessionStatus =
  | "requesting"
  | "connecting"
  | "ringing"
  | "connected"
  | "ended";

// Call Session events
export interface CallSessionAddedEvent extends NativeEvent {
  session: CallSession;
}

export interface CallSessionUpdatedEvent extends NativeEvent {
  session: CallSession;
}

export interface CallSessionRemovedEvent extends NativeEvent {
  id: string;
}

// Microphone/Camera Permission Status
export type PermissionStatus =
  | "granted"
  | "denied"
  | "undetermined"
  | "restricted"
  | "unknown";

// Audio Session

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

export interface AudioRoute {
  inputs: AudioPort[];
  outputs: AudioPort[];
}

export interface AudioPort {
  portType: AudioOutputPortType;
  portName: string;
  uid: string;
}

/**
 * Cross-platform audio output port type identifiers.
 * Both iOS and Android map their native audio device types to these shared values.
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

// Audio Session Events
export interface AudioSessionCallInfo {
  id: string;
  status: CallSessionStatus;
}

export interface AudioSessionActivatedEvent extends NativeEvent {
  calls: AudioSessionCallInfo[];
}

export interface AudioSessionDeactivatedEvent extends NativeEvent {
  calls: AudioSessionCallInfo[];
}

export interface AudioRouteChangedEvent extends NativeEvent {
  currentRoute: AudioRoute;
  /** Available audio output devices. Populated on Android; undefined on iOS. */
  availableRoutes?: AudioPort[];
}

// Capture Session
export interface CaptureSession {
  cameraPermission: PermissionStatus;
  /** Whether the device supports multitasking camera access (iOS 16+). */
  isMultitaskingCameraAccessSupported?: boolean;
}

// Call Action events
export interface CallActionEvent extends NativeEvent {
  id: string;
}

export interface OutgoingCallStartedEvent extends CallActionEvent {}

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

export interface IncomingCallReportedEvent extends CallActionEvent {}

export interface CallAnsweredEvent extends CallActionEvent {
  requestId: string;
}

export interface CallEndedEvent extends CallActionEvent {}

export type CallEndedReason =
  | "failed"
  | "remoteEnded"
  | "unanswered"
  | "answeredElsewhere"
  | "declinedElsewhere"
  | "unknown";

export interface CallReportedEnded extends CallActionEvent {
  reason: CallEndedReason;
}
export interface SetMutedActionEvent extends CallActionEvent {
  isMuted: boolean;
}
export interface VideoChangedEvent extends CallActionEvent {
  hasVideo: boolean;
}
export interface SetHeldActionEvent extends CallActionEvent {
  isOnHold: boolean;
}
export interface DTMFEvent extends CallActionEvent {
  digits: string;
}

// Call Intent Events

export type CallIntentHandleType = "phoneNumber" | "email" | "unknown";

export interface CallIntentReceivedEvent extends NativeEvent {
  handle: string;
  handleType: CallIntentHandleType;
  hasVideo: boolean;
}

// VoIP Push

/** A VoIP push token bundled with its type. */
export interface VoIPPushToken {
  /** The VoIP push token string. */
  token: string;
  /** The type of token this platform provides. */
  type: PushTokenType;
}

// VoIP Push Events

export interface VoIPPushTokenUpdatedEvent extends NativeEvent {
  /** The VoIP push token string, or undefined if invalidated */
  token?: string;
  /** The type of VoIP push token (e.g. "APNS_VOIP", "FCM"). */
  type: PushTokenType;
}
