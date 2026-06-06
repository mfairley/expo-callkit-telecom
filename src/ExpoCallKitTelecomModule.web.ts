import { NativeModule, registerWebModule } from "expo";

import type {
  AudioSession,
  CallAnsweredEvent,
  CallEndedEvent,
  CallEndedReason,
  CallIntentReceivedEvent,
  CallOptions,
  CallParticipant,
  CallReportedEnded,
  CallSession,
  CallSessionAddedEvent,
  CallSessionOrigin,
  CallSessionRemovedEvent,
  CallSessionStatus,
  CallSessionUpdatedEvent,
  CaptureSession,
  DTMFEvent,
  IncomingCallEvent,
  IncomingCallReportedEvent,
  NativeEventMeta,
  OutgoingCallStartedEvent,
  PermissionStatus,
  SetHeldActionEvent,
  SetMutedActionEvent,
  VideoChangedEvent,
  VoIPPushTokenUpdatedEvent,
} from "./Calls.types";

/**
 * Web platform implementation of `expo-callkit-telecom`.
 *
 * ## What this is
 *
 * The browser has no equivalent of CallKit (iOS) or Jetpack Core-Telecom
 * (Android): there is no OS-owned call UI, no lock-screen incoming-call screen,
 * and no VoIP push channel that can wake a terminated tab. This module therefore
 * cannot achieve native parity on web. It is a **best-effort** layer that keeps
 * the same JavaScript API working in the browser by:
 *
 * - Tracking call sessions in an in-memory state machine and emitting the same
 *   `onCallSession*` / `onCall*` events the native modules emit, so your media
 *   wiring (LiveKit / WebRTC) and UI code run unchanged.
 * - Surfacing incoming calls through the
 *   [Notifications API](https://developer.mozilla.org/en-US/docs/Web/API/Notifications_API)
 *   when notification permission has been granted.
 * - Exposing in-call controls (hang up, mute) through
 *   [`navigator.mediaSession`](https://developer.mozilla.org/en-US/docs/Web/API/MediaSession)
 *   when the browser supports the relevant action handlers.
 * - Bridging [Web Push](https://developer.mozilla.org/en-US/docs/Web/API/Push_API)
 *   for incoming calls: `registerVoIPPush()` requests notification permission and
 *   resolves a `PushManager` subscription (token type `"WEB_PUSH"`), and the module
 *   forwards `INCOMING_CALL` messages posted from your service worker to
 *   {@link reportIncomingCall}.
 *
 * As on iOS and Android, this module never touches media. Your app captures and
 * transports audio/video via `getUserMedia` + WebRTC and reacts to the events
 * below.
 *
 * ## Optional configuration
 *
 * Configure Web Push by assigning a config object on `globalThis` **before**
 * calling {@link registerVoIPPush}:
 *
 * ```ts
 * globalThis.expoCallKitTelecomWebConfig = {
 *   // VAPID public key used to create a PushManager subscription. Omit to only
 *   // read an existing subscription created elsewhere.
 *   vapidPublicKey: "BConfigured…",
 *   // Service worker that handles `push` events and posts INCOMING_CALL messages.
 *   serviceWorkerUrl: "/expo-callkit-sw.js",
 *   serviceWorkerScope: "/",
 * };
 * ```
 */
export interface ExpoCallKitTelecomWebConfig {
  /** VAPID public (application server) key for `PushManager.subscribe`. */
  vapidPublicKey?: string;
  /** URL of a service worker that forwards incoming-call pushes. */
  serviceWorkerUrl?: string;
  /** Scope to register the service worker under. Defaults to `"/"`. */
  serviceWorkerScope?: string;
}

/**
 * Message your service worker should `postMessage` to clients when a call push
 * arrives, so the page can report the incoming call:
 *
 * ```js
 * client.postMessage({ type: WEB_INCOMING_CALL_MESSAGE, event: incomingCallEvent });
 * ```
 */
export const WEB_INCOMING_CALL_MESSAGE = "EXPO_CALLKIT_TELECOM_INCOMING_CALL";

type ExpoCallKitTelecomEvents = {
  onCallSessionAdded: (event: CallSessionAddedEvent) => void;
  onCallSessionUpdated: (event: CallSessionUpdatedEvent) => void;
  onCallSessionRemoved: (event: CallSessionRemovedEvent) => void;
  onAudioSessionActivated: (event: { meta: NativeEventMeta; calls: { id: string; status: CallSessionStatus }[] }) => void;
  onAudioSessionDeactivated: (event: { meta: NativeEventMeta; calls: { id: string; status: CallSessionStatus }[] }) => void;
  onAudioRouteChanged: (event: { meta: NativeEventMeta; currentRoute: AudioSession["currentRoute"] }) => void;
  onIncomingCallReported: (event: IncomingCallReportedEvent) => void;
  onOutgoingCallStarted: (event: OutgoingCallStartedEvent) => void;
  onCallAnswered: (event: CallAnsweredEvent) => void;
  onCallEnded: (event: CallEndedEvent) => void;
  onCallReportedEnded: (event: CallReportedEnded) => void;
  onSetMutedAction: (event: SetMutedActionEvent) => void;
  onVideoChanged: (event: VideoChangedEvent) => void;
  onSetHeldAction: (event: SetHeldActionEvent) => void;
  onDTMF: (event: DTMFEvent) => void;
  onCallIntentReceived: (event: CallIntentReceivedEvent) => void;
  onVoIPPushTokenUpdated: (event: VoIPPushTokenUpdatedEvent) => void;
};

function randomUUID(): string {
  const cryptoObj = (globalThis as any)?.crypto;
  if (cryptoObj?.randomUUID) {
    return cryptoObj.randomUUID();
  }
  // RFC4122-ish fallback for environments without crypto.randomUUID.
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

/** Convert a base64url VAPID key into the Uint8Array `subscribe` expects. */
function urlBase64ToUint8Array(base64String: string): Uint8Array {
  const padding = "=".repeat((4 - (base64String.length % 4)) % 4);
  const base64 = (base64String + padding).replace(/-/g, "+").replace(/_/g, "/");
  const raw = atob(base64);
  const output = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) {
    output[i] = raw.charCodeAt(i);
  }
  return output;
}

class ExpoCallKitTelecomWebModule extends NativeModule<ExpoCallKitTelecomEvents> {
  private sessions = new Map<string, CallSession>();
  /** Maps a pending answer requestId back to its call id. */
  private pendingAnswers = new Map<string, string>();
  /** Live browser notifications keyed by call id, so we can close them. */
  private notifications = new Map<string, Notification>();
  private voipToken: string | null = null;
  private micPermission: PermissionStatus = "undetermined";
  private cameraPermission: PermissionStatus = "undetermined";
  private audioActive = false;
  private mediaSessionBound = false;

  constructor() {
    super();
    this.bindServiceWorkerMessages();
  }

  // ==========================================================================
  // Event helpers
  // ==========================================================================

  private meta(): NativeEventMeta {
    return { flushed: false, timestamp: new Date().toISOString() };
  }

  private emitSessionAdded(session: CallSession): void {
    this.emit("onCallSessionAdded", { meta: this.meta(), session });
  }

  private emitSessionUpdated(session: CallSession): void {
    this.emit("onCallSessionUpdated", { meta: this.meta(), session });
  }

  private setStatus(session: CallSession, status: CallSessionStatus): void {
    session.status = status;
    this.emitSessionUpdated({ ...session });
  }

  private activeId(): string | undefined {
    return this.sessions.keys().next().value;
  }

  // ==========================================================================
  // Audio session activation (best-effort, no real hardware control on web)
  // ==========================================================================

  private activateAudio(): void {
    if (this.audioActive) return;
    this.audioActive = true;
    this.emit("onAudioSessionActivated", {
      meta: this.meta(),
      calls: [...this.sessions.values()].map((s) => ({
        id: s.id,
        status: s.status,
      })),
    });
  }

  private deactivateAudioIfIdle(): void {
    if (!this.audioActive || this.sessions.size > 0) return;
    this.audioActive = false;
    this.emit("onAudioSessionDeactivated", { meta: this.meta(), calls: [] });
  }

  // ==========================================================================
  // Call sessions
  // ==========================================================================

  async getActiveCallSession(): Promise<CallSession | null> {
    const id = this.activeId();
    return id ? { ...this.sessions.get(id)! } : null;
  }

  // ==========================================================================
  // Audio / capture session snapshots
  // ==========================================================================

  getAudioSessionState(): AudioSession {
    this.refreshPermissions();
    return {
      isActive: this.audioActive,
      isOtherAudioPlaying: false,
      category: "playAndRecord",
      mode: "voiceChat",
      sampleRate: 48000,
      ioBufferDuration: 0,
      inputNumberOfChannels: 1,
      outputNumberOfChannels: 2,
      microphonePermission: this.micPermission,
      currentRoute: {
        inputs: [
          {
            portType: "builtInMic",
            portName: "Default microphone",
            uid: "web-default-input",
          },
        ],
        outputs: [
          {
            portType: "builtInSpeaker",
            portName: "Default speaker",
            uid: "web-default-output",
          },
        ],
      },
    };
  }

  getCaptureSessionState(): CaptureSession {
    this.refreshPermissions();
    return { cameraPermission: this.cameraPermission };
  }

  // These exist on iOS for AVAudioSession/RTCAudioSession orchestration. The
  // browser exposes no equivalent knobs, so they are intentional no-ops.
  setRTCAudioSessionConfiguration(_hasVideo: boolean): void {}
  prepareAudioSessionForCall(_hasVideo: boolean): void {
    this.refreshPermissions();
  }
  restoreAudioSession(): void {}
  setAudioSessionPortOverride(_enabled: boolean): void {}

  // ==========================================================================
  // Outgoing calls
  // ==========================================================================

  async startOutgoingCall(
    recipient: CallParticipant,
    options: CallOptions,
  ): Promise<string> {
    const id = randomUUID();
    const session = this.createSession(id, "outgoingApp", options, [recipient]);
    this.sessions.set(id, session);
    this.emitSessionAdded({ ...session });

    // Mirror native: the OS accepts the request on the next tick, then the app
    // wires media and reports it connected.
    setTimeout(() => {
      const current = this.sessions.get(id);
      if (!current) return;
      this.setStatus(current, "connecting");
      this.emit("onOutgoingCallStarted", { meta: this.meta(), id });
      this.activateAudio();
      this.bindMediaSession(recipient);
    }, 0);

    return id;
  }

  async reportOutgoingCallConnected(id: string): Promise<void> {
    const session = this.sessions.get(id);
    if (!session) return;
    session.connectedAt = new Date().toISOString();
    this.setStatus(session, "connected");
  }

  // ==========================================================================
  // Incoming calls
  // ==========================================================================

  async reportIncomingCall(event: IncomingCallEvent): Promise<void> {
    // Dedup on the server-side call id, matching native behaviour.
    for (const existing of this.sessions.values()) {
      if (existing.incomingCallEvent?.serverCallId === event.serverCallId) {
        return;
      }
    }

    const id = randomUUID();
    const session = this.createSession(
      id,
      "incoming",
      { hasVideo: event.hasVideo },
      [event.caller],
    );
    session.status = "ringing";
    session.incomingCallEvent = event;
    this.sessions.set(id, session);

    this.emitSessionAdded({ ...session });
    this.emit("onIncomingCallReported", { meta: this.meta(), id });
    this.showIncomingNotification(id, event);
  }

  async answerCall(id: string): Promise<void> {
    const session = this.sessions.get(id);
    if (!session) return;

    this.closeNotification(id);
    const requestId = randomUUID();
    this.pendingAnswers.set(requestId, id);

    this.setStatus(session, "connecting");
    this.emit("onCallAnswered", { meta: this.meta(), id, requestId });
    this.activateAudio();
    this.bindMediaSession(session.remoteParticipants[0]);
  }

  async fulfillIncomingCallAnswered(requestId: string): Promise<void> {
    const id = this.pendingAnswers.get(requestId);
    if (!id) return;
    this.pendingAnswers.delete(requestId);
    const session = this.sessions.get(id);
    if (!session) return;
    session.connectedAt = new Date().toISOString();
    this.setStatus(session, "connected");
  }

  async failIncomingCallConnected(requestId: string): Promise<void> {
    const id = this.pendingAnswers.get(requestId);
    this.pendingAnswers.delete(requestId);
    if (id) await this.reportCallEnded(id, "failed");
  }

  // ==========================================================================
  // Ending calls
  // ==========================================================================

  async endCall(id: string): Promise<void> {
    const session = this.sessions.get(id);
    if (!session) return;
    this.emit("onCallEnded", { meta: this.meta(), id });
    this.teardown(id);
  }

  async reportCallEnded(id: string, reason: CallEndedReason): Promise<void> {
    const session = this.sessions.get(id);
    if (!session) return;
    this.emit("onCallReportedEnded", { meta: this.meta(), id, reason });
    this.teardown(id);
  }

  private teardown(id: string): void {
    this.closeNotification(id);
    for (const [requestId, callId] of this.pendingAnswers) {
      if (callId === id) this.pendingAnswers.delete(requestId);
    }
    this.sessions.delete(id);
    this.emit("onCallSessionRemoved", { meta: this.meta(), id });
    this.clearMediaSession();
    this.deactivateAudioIfIdle();
  }

  // ==========================================================================
  // Mute / hold / video / DTMF
  // ==========================================================================

  async setMuted(id: string, muted: boolean): Promise<void> {
    const session = this.sessions.get(id);
    if (!session) return;
    session.isMuted = muted;
    this.emit("onSetMutedAction", { meta: this.meta(), id, isMuted: muted });
    this.emitSessionUpdated({ ...session });
  }

  async setHeld(id: string, onHold: boolean): Promise<void> {
    const session = this.sessions.get(id);
    if (!session) return;
    session.isOnHold = onHold;
    this.emit("onSetHeldAction", { meta: this.meta(), id, isOnHold: onHold });
    this.emitSessionUpdated({ ...session });
  }

  async reportVideo(id: string, enabled: boolean): Promise<void> {
    const session = this.sessions.get(id);
    if (!session) return;
    session.options = { ...session.options, hasVideo: enabled };
    this.emit("onVideoChanged", { meta: this.meta(), id, hasVideo: enabled });
    this.emitSessionUpdated({ ...session });
  }

  async playDTMF(id: string, digits: string): Promise<void> {
    const session = this.sessions.get(id);
    if (!session) return;
    session.dtmfDigits = digits;
    this.emit("onDTMF", { meta: this.meta(), id, digits });
  }

  // ==========================================================================
  // VoIP push (Web Push, best-effort)
  // ==========================================================================

  registerVoIPPush(): void {
    void this.registerWebPush();
  }

  getVoIPPushToken(): { token: string | null; type: string } {
    return { token: this.voipToken, type: "WEB_PUSH" };
  }

  private get config(): ExpoCallKitTelecomWebConfig {
    return (globalThis as any).expoCallKitTelecomWebConfig ?? {};
  }

  private async registerWebPush(): Promise<void> {
    try {
      if (
        typeof Notification !== "undefined" &&
        Notification.permission === "default"
      ) {
        await Notification.requestPermission();
      }
      this.refreshPermissions();

      const sw = (globalThis as any)?.navigator?.serviceWorker;
      if (!sw || typeof PushManager === "undefined") return;

      const { vapidPublicKey, serviceWorkerUrl, serviceWorkerScope } =
        this.config;

      let registration: ServiceWorkerRegistration;
      if (serviceWorkerUrl) {
        registration = await sw.register(serviceWorkerUrl, {
          scope: serviceWorkerScope ?? "/",
        });
      } else {
        registration = await sw.ready;
      }

      let subscription = await registration.pushManager.getSubscription();
      if (!subscription && vapidPublicKey) {
        subscription = await registration.pushManager.subscribe({
          userVisibleOnly: true,
          applicationServerKey: urlBase64ToUint8Array(
            vapidPublicKey,
          ) as BufferSource,
        });
      }

      this.voipToken = subscription ? JSON.stringify(subscription.toJSON()) : null;
      this.emit("onVoIPPushTokenUpdated", {
        meta: this.meta(),
        token: this.voipToken ?? undefined,
        type: "WEB_PUSH",
      });
    } catch (error) {
      console.warn("[expo-callkit-telecom] Web Push registration failed:", error);
    }
  }

  /**
   * Listens for `INCOMING_CALL` messages posted by the app's service worker
   * (from a `push` event) and reports them as incoming calls.
   */
  private bindServiceWorkerMessages(): void {
    const sw = (globalThis as any)?.navigator?.serviceWorker;
    if (!sw?.addEventListener) return;
    sw.addEventListener("message", (event: MessageEvent) => {
      const data = event?.data;
      if (data?.type === WEB_INCOMING_CALL_MESSAGE && data.event) {
        void this.reportIncomingCall(data.event as IncomingCallEvent);
      }
    });
  }

  // ==========================================================================
  // Browser integration: Notifications + Media Session
  // ==========================================================================

  private showIncomingNotification(id: string, event: IncomingCallEvent): void {
    if (
      typeof Notification === "undefined" ||
      Notification.permission !== "granted"
    ) {
      return;
    }
    try {
      const caller = event.caller;
      const notification = new Notification(
        caller.displayName ?? caller.phoneNumber ?? "Incoming call",
        {
          body: event.hasVideo ? "Incoming video call" : "Incoming call",
          icon: caller.avatarUrl,
          tag: id,
          requireInteraction: true,
        },
      );
      notification.onclick = () => {
        (globalThis as any)?.focus?.();
        void this.answerCall(id);
      };
      this.notifications.set(id, notification);
    } catch {
      // Some browsers only allow notifications from a service worker; ignore.
    }
  }

  private closeNotification(id: string): void {
    this.notifications.get(id)?.close();
    this.notifications.delete(id);
  }

  private bindMediaSession(participant?: CallParticipant): void {
    const mediaSession = (globalThis as any)?.navigator?.mediaSession;
    if (!mediaSession) return;

    try {
      if (typeof MediaMetadata !== "undefined" && participant) {
        mediaSession.metadata = new MediaMetadata({
          title: participant.displayName ?? participant.phoneNumber ?? "Call",
          artwork: participant.avatarUrl
            ? [{ src: participant.avatarUrl }]
            : undefined,
        });
      }
      if (this.mediaSessionBound) return;
      this.mediaSessionBound = true;

      this.safeSetActionHandler(mediaSession, "hangup", () => {
        const id = this.activeId();
        if (id) void this.endCall(id);
      });
      this.safeSetActionHandler(mediaSession, "togglemicrophone", () => {
        const id = this.activeId();
        const session = id ? this.sessions.get(id) : undefined;
        if (id && session) void this.setMuted(id, !session.isMuted);
      });
    } catch {
      // Action handlers are progressively supported; ignore unknown actions.
    }
  }

  private safeSetActionHandler(
    mediaSession: any,
    action: string,
    handler: () => void,
  ): void {
    try {
      mediaSession.setActionHandler(action, handler);
    } catch {
      // Unsupported action in this browser.
    }
  }

  private clearMediaSession(): void {
    const mediaSession = (globalThis as any)?.navigator?.mediaSession;
    if (!mediaSession || this.sessions.size > 0) return;
    this.mediaSessionBound = false;
    try {
      mediaSession.metadata = null;
      this.safeSetActionHandler(mediaSession, "hangup", null as any);
      this.safeSetActionHandler(mediaSession, "togglemicrophone", null as any);
    } catch {
      // ignore
    }
  }

  // ==========================================================================
  // Internal helpers
  // ==========================================================================

  private createSession(
    id: string,
    origin: CallSessionOrigin,
    options: CallOptions,
    participants: CallParticipant[],
  ): CallSession {
    return {
      id,
      options,
      origin,
      remoteParticipants: participants,
      status: "requesting",
      isMuted: false,
      isOnHold: false,
    };
  }

  private refreshPermissions(): void {
    const permissions = (globalThis as any)?.navigator?.permissions;
    if (!permissions?.query) return;
    permissions
      .query({ name: "microphone" as PermissionName })
      .then((status: PermissionStatus & { state: string }) => {
        this.micPermission = this.mapPermission(status.state);
      })
      .catch(() => {});
    permissions
      .query({ name: "camera" as PermissionName })
      .then((status: PermissionStatus & { state: string }) => {
        this.cameraPermission = this.mapPermission(status.state);
      })
      .catch(() => {});
  }

  private mapPermission(state: string): PermissionStatus {
    switch (state) {
      case "granted":
        return "granted";
      case "denied":
        return "denied";
      case "prompt":
        return "undetermined";
      default:
        return "unknown";
    }
  }
}

export default registerWebModule(
  ExpoCallKitTelecomWebModule,
  "ExpoCallKitTelecom",
);
