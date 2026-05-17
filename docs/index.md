---
layout: home
description: CallKit and Core-Telecom for React Native and Expo — VoIP push, incoming call UI, LiveKit-friendly audio. A modern react-native-callkeep alternative.

hero:
  name: expo-callkit-telecom
  text: CallKit & Core-Telecom for React Native + Expo
  tagline: One typed API across iOS and Android. VoIP push parsed natively. LiveKit-friendly audio session.
  actions:
    - theme: brand
      text: Getting started
      link: /getting-started
    - theme: alt
      text: View on GitHub
      link: https://github.com/mfairley/expo-callkit-telecom
    - theme: alt
      text: vs react-native-callkeep
      link: /vs-callkeep

features:
  - icon: 📱
    title: Native call UI
    details: '<a href="https://developer.apple.com/documentation/callkit">CallKit</a> on iOS, <a href="https://developer.android.com/reference/androidx/core/telecom/package-summary">Jetpack Core-Telecom</a> incoming-call notification + full-screen intent on Android. The module owns the system call UI, the audio session, and VoIP push — your app owns the media.'
  - icon: 🔔
    title: VoIP push, parsed natively
    details: '<a href="https://developer.apple.com/documentation/usernotifications/sending-voip-pushes">APNs VoIP</a> (<a href="https://developer.apple.com/documentation/pushkit">PushKit</a>) on iOS, <a href="https://firebase.google.com/docs/cloud-messaging">FCM</a> data messages on Android. Calls report to the OS before JS is running, so the cold-start case works without app-side glue.'
  - icon: 🎧
    title: LiveKit-friendly audio
    details: 'Integrates with <a href="https://webrtc.org/">WebRTC</a>''s <code>RTCAudioSession</code> in manual-audio mode, so <a href="https://livekit.io/">LiveKit</a> and plain WebRTC stacks work without app-side <a href="https://developer.apple.com/documentation/avfaudio/avaudiosession">AVAudioSession</a> coordination.'
  - icon: 🧩
    title: Typed, unified API
    details: 'One <code>CallSession</code> object. Three verbs — request, report, fulfill — that work the same on both platforms. No platform-only methods to keep straight.'
  - icon: 🛠️
    title: Expo config plugin
    details: '<a href="https://docs.expo.dev/config-plugins/introduction/">Expo config plugin</a> handles entitlements, background modes, microphone permission, ringtone bundling, and FCM service registration at prebuild.'
  - icon: 🆕
    title: Current platform APIs
    details: '<a href="https://www.swift.org/">Swift</a> on iOS (15.1+), <a href="https://kotlinlang.org/">Kotlin</a> + <a href="https://developer.android.com/jetpack/androidx/releases/core-telecom">androidx.core-telecom</a> on Android (minSdk 26). The path Google and Apple are pushing forward.'
---

## See it in action on iOS and Android

The runnable `example/` app drives the system call UI on both platforms — outgoing calls, banner-style incoming calls (while the device is in use), and full-screen incoming calls (when locked).

<DemoGrid />

## Frequently asked questions

### What is `expo-callkit-telecom`?

An Expo module that wraps CallKit on iOS and Jetpack Core-Telecom on Android behind one typed TypeScript API. The module owns the system call UI, the audio session, and VoIP push parsing; your app owns the media.

### Does it work with bare React Native, or only Expo?

It's distributed as an Expo module with a config plugin, so the smoothest path is an Expo project (managed or prebuild). Bare React Native apps that have adopted the Expo Modules API can also use it.

### How is it different from `react-native-callkeep`?

`react-native-callkeep` is built on Android's older `ConnectionService` API, Objective-C + Java, and leaves VoIP push parsing and `RTCAudioSession` coordination to the app. This module uses Jetpack `androidx.core-telecom` (Google's current recommendation), Swift + Kotlin, an Expo config plugin, and parses APNs VoIP and FCM data payloads natively. See [vs react-native-callkeep](./vs-callkeep) for the full side-by-side.

### Does it work with LiveKit?

Yes. The module owns the iOS audio session: it puts WebRTC's `RTCAudioSession` into manual-audio mode, then activates and deactivates it in lockstep with CallKit (when CallKit activates the session it enables WebRTC audio; when CallKit deactivates, it disables WebRTC audio and restores the pre-call configuration). Because of that, your app should **not** call LiveKit's `AudioSession.startAudioSession()` / `stopAudioSession()` (or otherwise activate `AVAudioSession` itself) — let the module handle it and wire LiveKit to the events the module emits.

### What are the minimum platform versions?

iOS 15.1 and Android API 26 (Android 8.0). The example app is exercised end-to-end against iOS 26 and Android 15 with Expo SDK 55, React Native 0.83, and the New Architecture enabled.

### Does it handle incoming calls when the app is terminated?

Yes. APNs VoIP (PushKit) on iOS and FCM data messages on Android are parsed natively before JS is running, so the call is reported to the OS in time for CallKit / Core-Telecom to show the incoming-call UI from a cold start.


