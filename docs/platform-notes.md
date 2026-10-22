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

### Call ended while the app is killed

On a killed app, Android shows the incoming-call UI fully natively — no React context ever starts unless the user answers. That means a **decline** (or any call end) in that state cannot be delivered as a JS event: `onCallEnded` is intentionally excluded from the cold-start replay queue, so the event is dropped and the app never learns about it. If your backend needs to know (so the caller stops ringing immediately instead of waiting for a server timeout), register a manifest `BroadcastReceiver` for the package-internal broadcast the module fires in exactly that case.

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

### Ending a call from the server

The reverse direction has the same problem. If the caller hangs up while the callee's phone is still ringing, a killed app has no way to act on it: the ring was reported natively, so there is no JS observer to call `reportCallEnded`, and the phone rings on until `incomingCallTimeout`.

Send a second FCM data message and the module ends the call natively:

```json
{
  "messageType": "callEnded",
  "callEnded": "{\"eventId\":\"7c1e...\",\"serverCallId\":\"call-123\",\"reason\":\"answeredElsewhere\"}"
}
```

`eventId` is required and must be unique per push, like the `eventId` on `IncomingCallEvent`: the module drops a repeat delivery of the same `eventId`, so a redelivered `callEnded` can't end a later ring for the same call. `serverCallId` is the one you set on the `IncomingCallEvent` when you started the call. `reason` is optional and takes any `CallEndedReason` (`remoteEnded`, `answeredElsewhere`, `declinedElsewhere`, `unanswered`, `failed`, `unknown`); it defaults to `remoteEnded`, and an unrecognized value falls back to `remoteEnded` with a warning in logcat.

The module looks up the session reported for that `serverCallId` and ends it through the same path as `reportCallEnded` from JS: the system call UI is dismissed and JS (if it's running) receives `onCallReportedEnded` with the `reason`. It does **not** check the call's state, so a `callEnded` for a call this device has already answered hangs it up — only send it for calls the device should stop. A `serverCallId` with no session on the device (already ended, or never delivered) is ignored.

These messages are consumed by the module and are never passed on to `expo-notifications`, so they don't reach notification listeners or background notification tasks; react to `onCallReportedEnded` instead. On a killed app that event is delivered through the call-event broadcast above.

The example server sends it with `bun send-test-push.ts --end --serverCallId call-123 [--reason answeredElsewhere]`.

### Customising the incoming-call screen

The full-screen incoming-call UI (`IncomingCallActivity`) takes its labels and colours from ordinary Android resources, and the incoming-call notification shares its labels, so an app can translate or restyle them without forking the module. The names and their defaults:

| String | Default | Used for |
|---|---|---|
| `expo_callkit_telecom_incoming_call` | Incoming call | subtitle of a voice call, on the screen and the notification |
| `expo_callkit_telecom_incoming_video_call` | Incoming video call | subtitle of a video call, on the screen and the notification |
| `expo_callkit_telecom_answer` | Answer | Answer button label and accessibility description |
| `expo_callkit_telecom_decline` | Decline | Decline button label and accessibility description |
| `expo_callkit_telecom_unknown_caller` | Unknown | caller name when the call carries neither a display name nor a handle, on the screen and the notification |

| Colour | Default | Used for |
|---|---|---|
| `expo_callkit_telecom_background` | `#0D0D0D` | screen background |
| `expo_callkit_telecom_text_primary` | `#FFFFFF` | caller name |
| `expo_callkit_telecom_text_secondary` | `#8E8E93` | call-type subtitle, Answer/Decline labels |
| `expo_callkit_telecom_text_header` | `#8E8E93` | app name in the header |
| `expo_callkit_telecom_avatar_fill` | `#1C1C2E` | avatar disc |
| `expo_callkit_telecom_avatar_text` | `#FFFFFF` | initial or placeholder glyph on the avatar disc |
| `expo_callkit_telecom_answer_fill` | `#34C759` | Answer button |
| `expo_callkit_telecom_decline_fill` | `#FF3B30` | Decline button |
| `expo_callkit_telecom_on_fill` | `#FFFFFF` | glyphs on the Answer/Decline buttons |
| `expo_callkit_telecom_ripple_on_fill` | `#4DFFFFFF` | press ripple on the Answer/Decline buttons |

Android resource merging gives your app's resources precedence over the module's, so override by defining the same names in your app: `res/values/strings.xml` and `res/values/colors.xml` for the defaults, `res/values-<lang>/strings.xml` for each translation, and `res/values-night/colors.xml` for a dark variant (the module ships none — the defaults above apply in both UI modes; the activity's theme keeps dark system bars where they are drawn, so a light background override is framed by them). Define the English defaults in your app as well, not only the translations: Android lint's `ExtraTranslation` check does not count the module's defaults, so it can flag a translated name with no entry in your app's own `res/values/strings.xml` and may fail the release build's `lintVital`.

In an Expo managed project `android/` is generated, so use Expo's own hooks. Translations go through [`expo.locales`](https://docs.expo.dev/guides/localization/#translating-app-metadata) in app config (`"locales": { "nb": "./locales/nb.json" }`); at prebuild Expo writes each locale file's `android` keys into `res/values-b+<lang>/strings.xml`:

```jsonc
// locales/nb.json
{
  "android": {
    "expo_callkit_telecom_incoming_call": "Innkommende anrop",
    "expo_callkit_telecom_incoming_video_call": "Innkommende videoanrop",
    "expo_callkit_telecom_answer": "Svar",
    "expo_callkit_telecom_decline": "Avvis",
    "expo_callkit_telecom_unknown_caller": "Ukjent"
  }
}
```

The English defaults (for the lint rule above, or to reword them) and the colours need a small [config plugin](https://docs.expo.dev/config-plugins/mods/) built from `expo/config-plugins`: `withStringsXml` with `AndroidConfig.Strings.setStringItem` for `values/strings.xml`, `withAndroidColors` with `AndroidConfig.Colors.assignColorValue` for `values/colors.xml`, and `withAndroidColorsNight` for `values-night/colors.xml`.

Like every Android resource, the screen follows the device locale — or the app's per-app language on Android 13+ (`LocaleManager`; the `AppCompatDelegate` shim for older versions does not reach this screen, which is a plain `android.app.Activity`) — not a language setting your app keeps in JS. If your app has its own language switch, set the per-app locale from it so the native screen matches.

## VoIP push token types

The VoIP push token type is reported as `"APNS_VOIP"` on iOS and `"FCM"` on Android — send both to your backend so it knows which transport to use.

## Keeping connections alive in the background

This module hands the OS a CallKit/Core-Telecom call, which keeps the *process* alive during a call — but JS timers (`setInterval`, `setTimeout`) and JS-side network heartbeats are still subject to background throttling once the screen locks. If your media stack needs an app-level heartbeat (e.g. a WebSocket signalling channel) to survive the background, pair this module with [`react-native-nitro-keepalive-timer`](https://www.npmjs.com/package/react-native-nitro-keepalive-timer) to get native timers that fire reliably while a call is active.
