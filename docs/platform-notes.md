---
description: Platform requirements for expo-callkit-telecom — iOS 16.0+ (CallKit + PushKit), Android API 26+ (Jetpack androidx.core-telecom). Keep-alive notes for background WebSocket signalling.
---

# Platform notes

## iOS

- Requires the `voip` background mode and a VoIP push certificate.
- Uses [CallKit](https://developer.apple.com/documentation/callkit) + [PushKit](https://developer.apple.com/documentation/pushkit) + [WebRTC](https://webrtc.org/)'s `RTCAudioSession` for manual audio control.
- Minimum iOS version: **16.0**.

## Expo

- Supports Expo SDK **55** and later.

## Android

- Requires [`MANAGE_OWN_CALLS`](https://developer.android.com/reference/android/Manifest.permission#MANAGE_OWN_CALLS) permission.
- Minimum SDK: **26** (Android 8.0).
- Uses [`androidx.core:core-telecom`](https://developer.android.com/develop/connectivity/telecom/voip-app/telecom).
- Incoming calls come via [FCM](https://firebase.google.com/docs/cloud-messaging) data messages — the config plugin registers `ExpoCallKitTelecomMessagingService` automatically.
- Muting a call (from `setMuted` or the system call UI) also mutes the device microphone via [`AudioManager.setMicrophoneMute`](https://developer.android.com/reference/android/media/AudioManager#setMicrophoneMute(boolean)). The mute is device-wide, so the module applies it only while call audio is active and clears it when the last call ends. This adds the [`MODIFY_AUDIO_SETTINGS`](https://developer.android.com/reference/android/Manifest.permission#MODIFY_AUDIO_SETTINGS) permission to your app's manifest; it's granted at install, with no runtime prompt.

### Ending a call from the server

This is the reverse direction of [Call ended while the app is killed](#call-ended-while-the-app-is-killed). If the caller hangs up while the callee's phone is still ringing, a killed app has no way to act on it: the ring was reported natively, so there is no JS observer to call `reportCallEnded`, and the phone rings on until `incomingCallTimeout`.

Send a second FCM data message and the module ends the call natively:

```json
{
  "messageType": "callEnded",
  "callEnded": "{\"eventId\":\"7c1e...\",\"serverCallId\":\"call-123\",\"reason\":\"answeredElsewhere\"}"
}
```

`eventId` is required and must be unique per push, like the `eventId` on `IncomingCallEvent`: the module drops a repeat delivery of the same `eventId`, so a redelivered `callEnded` can't end a later ring for the same call. `serverCallId` is the one you set on the `IncomingCallEvent` when you started the call. `reason` is optional and takes any `CallEndedReason` (`remoteEnded`, `answeredElsewhere`, `declinedElsewhere`, `unanswered`, `failed`, `unknown`); it defaults to `remoteEnded`, and an unrecognized value falls back to `remoteEnded` with a warning in logcat.

The module looks up the session reported for that `serverCallId` and ends it through the same path as `reportCallEnded` from JS: the system call UI is dismissed and JS (if it's running) receives `onCallReportedEnded` with the `reason`. It does **not** check the call's state, so a `callEnded` for a call this device has already answered hangs it up — only send it for calls the device should stop. A `serverCallId` with no session on the device (already ended, or never delivered) is ignored.

These messages are consumed by the module and are never passed on to `expo-notifications`, so they don't reach notification listeners or background notification tasks; react to `onCallReportedEnded` instead. On a killed app that event is delivered through the call-event broadcast described under [Call ended while the app is killed](#call-ended-android).

The example server sends it with `bun send-test-push.ts --end --serverCallId call-123 [--reason answeredElsewhere]`.

## Call ended while the app is killed

On both platforms, a call the user ends from the native call UI while the app is killed — typically a **decline** — produces an `onCallEnded` that no JS listener can receive; instead of dropping it, the module hands it to native app code so your backend can still learn about it.

### Android {#call-ended-android}

On Android, a killed app shows the incoming-call UI fully natively — no React context ever starts unless the user answers. That means a **decline** (or any call end) in that state cannot be delivered as a JS event: `onCallEnded` is intentionally excluded from the cold-start replay queue, so the event is dropped and the app never learns about it. If your backend needs to know (so the caller stops ringing immediately instead of waiting for a server timeout), register a manifest `BroadcastReceiver` for the package-internal broadcast the module fires in exactly that case.

Register the receiver via the config plugin, which writes the manifest entry. You supply the receiver class (e.g. in a [local Expo module](https://docs.expo.dev/modules/get-started/) or your `android/` source):

```js
// app.config.js / app.json plugin props
[
  "expo-callkit-telecom",
  { androidEventReceiver: ".CallEndedReceiver" },
]
```

```kotlin
class CallEndedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getStringExtra("eventName") != "onCallEnded") return
        val payload = intent.getStringExtra("payload")?.let { JSONObject(it) } ?: return
        val session = payload.optJSONObject("session")
        val incomingCall = session?.optJSONObject("incomingCallEvent")
        val serverCallId = incomingCall?.optString("serverCallId")
        val metadata = incomingCall?.optJSONObject("metadata")
        // e.g. POST a decline to your backend (use goAsync() for the network call)
    }
}
```

The broadcast carries two extras: `eventName` (the call event that couldn't reach JS) and `payload`, a JSON string of the exact body JS would have received. Events that can't reach a live JS observer **and** aren't queued for cold-start replay (e.g. `onCallEnded`, `onCallReportedEnded`) are broadcast — filter on `eventName` for the ones you care about (the example above handles only `onCallEnded`). For these terminal events the payload embeds the full `session`, so your backend ids (`serverCallId`) and any push `metadata` (auth tokens, routing ids) are available without app-side state. The broadcast fires **only** when the event would otherwise be lost; an alive app with a mounted listener keeps receiving the normal JS event, and events that flush to JS on the next launch are never broadcast (so there's no double-delivery).

A complete working setup ships in the example app: `example/client/` sets `androidEventReceiver` in `app.config.ts` and copies `plugins/CallEndedReceiver.kt` into the generated android project — see "Testing system → app paths" in the example README for how to exercise it.

### iOS {#call-ended-ios}

iOS has the same gap. A VoIP push launches the killed app in the background, CallKit rings natively, and a **decline** from the system UI emits `onCallEnded` before JS is observing it — so it's dropped, and the app never learns about it. Whenever an event can't reach a live JS observer **and** isn't queued for cold-start replay (e.g. `onCallEnded`, `onCallReportedEnded`), the module posts a `NotificationCenter` notification with the same name and keys as the Android broadcast: `Notification.Name("expo.modules.callkittelecom.ACTION_CALL_EVENT")`, with `userInfo["eventName"]` and `userInfo["payload"]`. The payload is the exact body JS would have received, as a `[String: Any]` dictionary rather than a JSON string; for the terminal events it embeds the full `session` just like on Android. It is posted synchronously on the main thread.

Observe it from an [`ExpoAppDelegateSubscriber`](https://docs.expo.dev/modules/appdelegate-subscribers/), which exists as soon as the app delegate does, before PushKit can deliver a call. A [local Expo module](https://docs.expo.dev/modules/get-started/)'s `OnCreate` runs when the React host starts and can be too late for a cold-launch decline, and JS itself is by definition not yet observing:

```swift
NotificationCenter.default.addObserver(
  forName: Notification.Name("expo.modules.callkittelecom.ACTION_CALL_EVENT"),
  object: nil, queue: nil
) { notification in
  guard notification.userInfo?["eventName"] as? String == "onCallEnded",
    let payload = notification.userInfo?["payload"] as? [String: Any]
  else { return }
  let session = payload["session"] as? [String: Any]
  let incomingCall = session?["incomingCallEvent"] as? [String: Any]
  let serverCallId = incomingCall?["serverCallId"] as? String
  let metadata = incomingCall?["metadata"] as? [String: Any]
  // e.g. POST a decline to your backend. Begin a UIApplication background task
  // around the request: the module only keeps the process alive until the
  // notification has been posted.
}
```

The same rules apply as on Android: each event resolves to exactly one path — a live JS observer, the cold-start replay queue, or this notification — so it fires **only** when the event would otherwise be lost; an alive app with a mounted listener keeps receiving the normal JS event, and events that flush to JS on the next launch are never posted.

Only the system-UI end path holds the process open for you: when the user ends or declines the call from the CallKit screen, the module keeps a `UIApplication` background task alive until the notification has been posted, so an observer can begin its own task for the network request. Other posted events — for instance `onCallReportedEnded` from the unanswered-call timeout — are best-effort: nothing stops iOS from suspending a background-launched process before they are emitted.

A complete working setup ships in the example app: `example/client/modules/call-ended-observer/` is a local Expo module whose `ExpoAppDelegateSubscriber` observes the notification and logs the payload — see "Testing system → app paths" in the example README for how to exercise it.

## VoIP push token types

The VoIP push token type is reported as `"APNS_VOIP"` on iOS and `"FCM"` on Android — send both to your backend so it knows which transport to use.

## Keeping connections alive in the background

This module hands the OS a CallKit/Core-Telecom call, which keeps the *process* alive during a call — but JS timers (`setInterval`, `setTimeout`) and JS-side network heartbeats are still subject to background throttling once the screen locks. If your media stack needs an app-level heartbeat (e.g. a WebSocket signalling channel) to survive the background, pair this module with [`react-native-nitro-keepalive-timer`](https://www.npmjs.com/package/react-native-nitro-keepalive-timer) to get native timers that fire reliably while a call is active.
