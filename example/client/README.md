# expo-callkit-telecom example client

A media-free Expo app that exercises every API surface of `expo-callkit-telecom` — the native call UI, audio session, mute, the answer/end flows, and VoIP push parsing — without any media library or backend integration.

Because there's no media, you don't get audio in the call. Everything else is real: CallKit / Telecom is talking to the actual OS subsystems, the audio session activates, the event handlers fire, and the JS-side state syncs through.

## What the UI shows

- **VoIP device token** — once the OS registers the app for VoIP push, the token is logged to the Metro / device console (`VoIP device token: ...`). Copy it into `example/server/.env` to send yourself a test push (see `../server/`).
- **Call Session** — pills showing whether a session is active, its `status` (`requesting` → `connecting` → `ringing` → `connected` → `ended`), audio vs video, and muted state.
- **Audio Session** — pills showing whether the OS audio session is active and whether output is routed to the earpiece or speaker.
- **Actions** — every button below; see the next section.
- **Event Log** — running log of every listener firing (`CallSessionAdded`, `AudioRouteChanged`, `SetMutedAction`, `DTMF`, `VoIPPushTokenUpdated`, etc.).

## What the Actions card lets you test

Each button maps to a specific API on the module. Buttons that don't apply to the current call state are disabled.

- **Start outgoing call** — `startOutgoingCall(...)`; prompts for audio or video. Goes through the system outgoing-call flow (`CXStartCallAction` on iOS, Telecom `addCall` with `OutgoingCallAttributes` on Android).
- **Simulate incoming call** — `reportIncomingCall(...)` locally with a synthesised event, so you can exercise the answer flow without a backend or push. Prompts for audio or video.
- **Connect call** — acks the call to the OS once your "media" is "ready". For an incoming call it calls `fulfillIncomingCallConnected(requestId)`; for an outgoing call it calls `reportOutgoingCallConnected(id)`. CallKit / Telecom then transitions the session to `connected`.
- **Fail connection** — the negative ack: `failIncomingCallConnected(...)` for incoming, `reportCallEnded(id, "failed")` for outgoing. Useful for testing your media-side failure paths.
- **Mute / Unmute** — `setMuted(id, !isMuted)`. Routes through the system, so the native mute button on the CallKit UI / Telecom notification stays in sync.
- **Hold / Resume** — `setHeld(id, !isOnHold)`. Same system-path round-trip as mute.
- **Speaker / Earpiece** — `setAudioSessionPortOverride(toSpeaker)`. Disabled for video calls (the system manages that automatically). Watch the *Audio Session* pills update live.
- **Enable / Disable video** — `reportVideo(id, hasVideo)`. Tells the OS whether the call currently has video, independent of how it started.
- **End call (local)** — `endCall(id)`. The app-side hangup (analog: user taps "End" in your UI).
- **Remote ended** — `reportCallEnded(id, "remoteEnded")`. Simulates the other party hanging up, so you can verify the OS shows the correct end reason.

## Testing system → app paths

A few flows are best exercised by interacting with the system UI directly rather than the in-app buttons:

- Tap the native **mute** / **hold** / **speaker** buttons on the CallKit UI (iOS) or Telecom notification (Android) — the *Call Session* / *Audio Session* pills update and the corresponding `SetMutedAction` / `SetHeldAction` / `AudioRouteChanged` events appear in the log.
- Plug in / unplug headphones, connect a Bluetooth device, etc. — `AudioRouteChanged` fires.
- On iOS, tap a contact in the **Recents** list or say *"call <displayName>"* to Siri — `CallIntentReceived` fires.
- Force-quit the app and send a push via `example/server/` — the native parser shows the call without JS running.

## Running

A real device is required — CallKit and Telecom don't work in simulators.

From the repo root:

```sh
bun install
cd example/client
bunx expo prebuild --clean
bun run ios --device
```

