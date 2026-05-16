# Getting started

`expo-callkit-telecom` is opinionated about *system integration* and unopinionated about *media*. You wire your media library (LiveKit, plain WebRTC, etc.) to the events the module emits.

::: info Verified against
This release is exercised end-to-end on real devices in the `example/` app against **iOS 26**, **Android 15**, **Expo SDK 55**, **React Native 0.83**, and the **LiveKit RN SDK**, with the **New Architecture** enabled. Minimum supported versions: iOS 15.1, Android API 26.
:::

## Install

```sh
bun add expo-callkit-telecom
```

Add the config plugin to `app.json` / `app.config.ts`. Minimal form:

```jsonc
{
  "expo": {
    "plugins": ["expo-callkit-telecom"]
  }
}
```

With custom ringtone and dialtone:

```jsonc
{
  "expo": {
    "plugins": [
      [
        "expo-callkit-telecom",
        {
          "sounds": [
            "./assets/sounds/ringtone.caf",
            "./assets/sounds/dialtone.caf"
          ],
          "defaultRingtoneIos": "ringtone.caf",
          "defaultRingtoneAndroid": "ringtone.caf",
          "defaultDialtone": "dialtone.caf",
          "incomingCallTimeout": 45,
          "outgoingCallTimeout": 60,
          "microphonePermission": "$(PRODUCT_NAME) needs the microphone to make calls."
        }
      ]
    ]
  }
}
```

Files in `sounds` are copied into the iOS bundle and Android raw resources at prebuild time. The full prop type is `ExpoCallKitTelecomPluginProps` in `plugin/src/`.

## Concepts

The TypeScript API is organised into three verbs:

| Verb         | Direction              | Examples                                                              |
| ------------ | ---------------------- | --------------------------------------------------------------------- |
| **Request**  | App → System           | `startOutgoingCall`, `answerCall`, `endCall`, `setMuted`              |
| **Report**   | App → System (state)   | `reportIncomingCall`, `reportOutgoingCallConnected`, `reportCallEnded` |
| **Fulfill**  | App → System (ack)     | `fulfillIncomingCallConnected`                                        |

Events flow the other way (System → App) via `addXxxListener`.

## Registering for VoIP push

```ts
import {
  registerVoIPPush,
  useVoIPPushToken,
} from "expo-callkit-telecom";

// Once, early in app lifecycle:
registerVoIPPush();

// In a React component:
function App() {
  const voip = useVoIPPushToken();
  useEffect(() => {
    if (voip) {
      // voip.type is "APNS_VOIP" on iOS, "FCM" on Android.
      sendToBackend(voip.token, voip.type);
    }
  }, [voip]);
}
```

## Example app

The repo's `example/` contains a runnable Expo app (`example/client/`) and a zero-dep push-sender script (`example/server/`). See their READMEs for setup and how to validate VoIP push end-to-end.

## Next steps

- [VoIP push payload shape](./voip-push)
- [Platform notes](./platform-notes)
- [API reference](./api/)
- [Comparison with `react-native-callkeep`](./vs-callkeep)
