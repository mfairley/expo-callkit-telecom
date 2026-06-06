---
description: Best-effort web support for expo-callkit-telecom — the same call API and events in the browser, backed by the Notifications API, navigator.mediaSession, and Web Push. What works, what doesn't, and how to wire it up.
---

# Web support

`expo-callkit-telecom` ships a **best-effort** web implementation so the same
JavaScript API and events run in the browser. It is **not** native parity, and
it can't be: the web platform has no equivalent of [CallKit](https://developer.apple.com/documentation/callkit)
or [Jetpack Core-Telecom](https://developer.android.com/develop/connectivity/telecom/voip-app/telecom).

::: warning The browser has no native call layer
There is no OS-owned, lock-screen incoming-call UI on the web, and no VoIP push
channel that can wake a terminated tab the way APNs VoIP or FCM high-priority
data messages do. Treat web as a graceful fallback, not a replacement for the
native call experience.
:::

## What you get on web

The web module mirrors the native modules where the platform allows, and degrades
gracefully where it doesn't:

| Capability | iOS / Android | Web |
| --- | --- | --- |
| Same JS API + events (`startOutgoingCall`, `reportIncomingCall`, `onCall*`, `onCallSession*`, mute/hold/video/DTMF) | ✅ | ✅ in-memory state machine |
| Incoming-call UI | Native full-screen / banner | [Notification](https://developer.mozilla.org/en-US/docs/Web/API/Notifications_API) (foreground/granted only) |
| In-call controls (hang up, mute) | Native call UI | [`navigator.mediaSession`](https://developer.mozilla.org/en-US/docs/Web/API/MediaSession) action handlers, where supported |
| Wake a closed/terminated app for a call | ✅ VoIP push (PushKit / FCM) | ❌ not possible |
| Lock-screen call screen | ✅ | ❌ |
| Audio session / route control | ✅ | No-op — the browser owns routing |
| Media (audio/video) | Your app (LiveKit / WebRTC) | Your app (`getUserMedia` + WebRTC) — unchanged |

The audio-session functions (`prepareAudioSessionForCall`, `restoreAudioSession`,
`setAudioSessionPortOverride`, `setRTCAudioSessionConfiguration`) are intentional
no-ops on web. `getAudioSession()` / `getCaptureSession()` return best-effort
snapshots, including microphone/camera permission read from the
[Permissions API](https://developer.mozilla.org/en-US/docs/Web/API/Permissions_API)
when available.

## How call flows behave

The event sequences match native, so your existing listeners work without
branching on platform:

- **Outgoing** — `startOutgoingCall` → `onCallSessionAdded` (`requesting`) →
  `onOutgoingCallStarted` + `onAudioSessionActivated` (`connecting`) → you wire
  media → `reportOutgoingCallConnected` (`connected`) → `endCall` →
  `onCallEnded` → `onCallSessionRemoved`.
- **Incoming** — `reportIncomingCall` → `onCallSessionAdded` (`ringing`) +
  `onIncomingCallReported` (and a browser Notification, if permitted) → user
  answers (Notification click or your UI calling `answerCall`) → `onCallAnswered`
  → you wire media → `fulfillIncomingCallConnected` (`connected`).

Incoming calls are de-duplicated by `serverCallId`, matching native behaviour.

## Incoming calls via Web Push

Calling [`registerVoIPPush()`](/api/#registervoippush) on web:

1. Requests [`Notification`](https://developer.mozilla.org/en-US/docs/Web/API/Notification/requestPermission)
   permission.
2. Resolves a [`PushManager`](https://developer.mozilla.org/en-US/docs/Web/API/PushManager)
   subscription and emits it through `onVoIPPushTokenUpdated` with type
   `"WEB_PUSH"`. The token is the JSON-encoded push subscription — send it to
   your backend and push to it with your VAPID keys.

Because a `push` event is delivered to your **service worker** (not the page),
your service worker shows the notification and tells the page to report the call.
The module listens for a message of this shape and calls `reportIncomingCall`
for you:

```js
// In your service worker, on a call push:
self.addEventListener("push", (event) => {
  const incomingCallEvent = event.data.json(); // your IncomingCallEvent
  event.waitUntil(
    (async () => {
      await self.registration.showNotification(
        incomingCallEvent.caller.displayName ?? "Incoming call",
        { body: "Incoming call", requireInteraction: true, tag: incomingCallEvent.serverCallId },
      );
      const clients = await self.clients.matchAll({ includeUncontrolled: true, type: "window" });
      for (const client of clients) {
        client.postMessage({
          type: "EXPO_CALLKIT_TELECOM_INCOMING_CALL",
          event: incomingCallEvent,
        });
      }
    })(),
  );
});
```

## Configuration

Configure Web Push by assigning a config object on `globalThis` **before**
calling `registerVoIPPush()`:

```ts
globalThis.expoCallKitTelecomWebConfig = {
  // VAPID public (application server) key used to create the subscription.
  // Omit to only read an existing subscription created elsewhere in your app.
  vapidPublicKey: "BPublicKey…",
  // Service worker that handles `push` events and posts INCOMING_CALL messages.
  serviceWorkerUrl: "/expo-callkit-telecom-sw.js",
  serviceWorkerScope: "/",
};
```

If you don't provide a `vapidPublicKey`, the module won't create a new
subscription — it will reuse an existing one from `pushManager.getSubscription()`,
which is useful if your app already manages push elsewhere.

## Notes & caveats

- **HTTPS required.** Notifications, service workers, and Web Push only work on
  secure origins (`https://`, or `localhost` in development).
- **Permission prompts.** Browsers increasingly require a user gesture before
  granting notification permission; call `registerVoIPPush()` from a user action.
- **No background wake-up.** If the tab is fully closed, the page can't report
  the call. Your service worker can still show a notification from a push, and
  the call is reported once the user opens the app.
- **Media is still yours.** The module never captures or transports audio/video
  on any platform — wire your media library to the same events you use on
  iOS/Android.
