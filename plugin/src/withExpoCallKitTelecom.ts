import { type ConfigPlugin, createRunOncePlugin } from "expo/config-plugins";

import pkg from "../../package.json";
import { withExpoCallKitTelecomAndroid } from "./withExpoCallKitTelecomAndroid";
import { withExpoCallKitTelecomIos } from "./withExpoCallKitTelecomIos";

export type ExpoCallKitTelecomPluginProps = {
  /**
   * Custom message for microphone permission prompt. Set to false to skip.
   * @platform ios
   */
  microphonePermission?: string | false;
  /**
   * Custom message for camera permission prompt. Set to false to skip.
   * @platform ios
   */
  cameraPermission?: string | false;
  /**
   * Timeout in seconds for incoming calls before they are marked as unanswered.
   * @default 45
   * @platform ios
   * @platform android
   */
  incomingCallTimeout?: number;
  /**
   * Timeout in seconds for outgoing calls to connect before they are marked as unanswered.
   * @default 60
   * @platform ios
   * @platform android
   */
  outgoingCallTimeout?: number;
  /**
   * Timeout in seconds for waiting for the call to connect after answering.
   * @default 30
   * @platform ios
   * @platform android
   */
  fulfillAnswerCallTimeout?: number;
  /**
   * Whether completed calls appear in the phone's call history (Recents).
   * Maps to CallKit's `CXProviderConfiguration.includesCallsInRecents`.
   * @default true
   * @platform ios
   */
  includesCallsInRecents?: boolean;
  /**
   * Array of sound file paths (relative to project root) to include in the app.
   * These files will be copied into the iOS bundle and Android raw resources.
   * .wav recommended
   * @platform ios
   * @platform android
   */
  sounds?: string[];
  /**
   * The default ringtone for incoming calls on iOS (CallKit).
   * Can be the filename (with extension) of one of the provided sounds,
   * or 'default' to use the system ringtone.
   * @default 'default'
   * @platform ios
   */
  defaultRingtoneIos?: string;
  /**
   * The default ringtone for incoming calls on Android (notification channel).
   * Can be the filename (with extension) of one of the provided sounds,
   * or 'default' to use the system ringtone.
   * @default 'default'
   * @platform android
   */
  defaultRingtoneAndroid?: string;
  /**
   * The default dialtone to play during outgoing calls while connecting.
   * Must be the filename (with extension) of one of the provided sounds.
   * @platform ios
   * @platform android
   */
  defaultDialtone?: string;
  /**
   * Path (relative to project root) to a PNG shown as the app's icon on the
   * iOS CallKit call screen (`CXProviderConfiguration.iconTemplateImageData`).
   * CallKit renders it as a *template*: only the alpha channel is used (RGB is
   * ignored) and the system tints it. A ~40x40pt square PNG whose alpha
   * describes the glyph is recommended. When omitted, CallKit derives a mask
   * from the app icon, which renders as a solid square for opaque icons.
   * @platform ios
   */
  iconTemplateIos?: string;
  /**
   * Class name of a `BroadcastReceiver` to register for the module's call-event
   * broadcast, fired when a call event can't reach a live JS observer (e.g. the
   * user declines an incoming call while the app is killed). The plugin writes
   * the manifest `<receiver>` + `<intent-filter>`. You supply the receiver class
   * (e.g. via a local Expo module); the plugin does not generate it.
   *
   * Accepts a manifest-relative name (`.CallEventReceiver`) or a fully-qualified
   * one (`com.acme.app.CallEventReceiver`). See "Call ended while the app is
   * killed" in the docs.
   * @platform android
   */
  androidEventReceiver?: string;
  /**
   * Ring on a second notification channel at `IMPORTANCE_DEFAULT` while the
   * app is on screen (any activity in its process other than the module's own
   * `IncomingCallActivity`): same ringtone and vibration, but no heads-up
   * banner and no full-screen intent, so an incoming-call screen the app draws
   * itself is not covered by the system banner (which on some OEM builds stays
   * pinned over it for the whole ring). The ring follows the app: it moves to
   * the heads-up channel when the app leaves the screen and back when it
   * returns. Lock-screen, background and killed-app rings are unchanged.
   *
   * Enable this only if the app renders its own incoming-call UI on
   * `onCallSessionAdded` / `onIncomingCallReported`; otherwise an incoming call
   * shows no UI while the app is open. See "Ringing while the app is on
   * screen" in the docs.
   * @default false
   * @platform android
   */
  androidInAppRingChannel?: boolean;
};

const withExpoCallKitTelecom: ConfigPlugin<ExpoCallKitTelecomPluginProps | void> = (
  config,
  props,
) => {
  const opts: ExpoCallKitTelecomPluginProps = props ?? {};
  config = withExpoCallKitTelecomAndroid(config, opts);
  config = withExpoCallKitTelecomIos(config, opts);

  return config;
};

export default createRunOncePlugin(withExpoCallKitTelecom, pkg.name, pkg.version);
