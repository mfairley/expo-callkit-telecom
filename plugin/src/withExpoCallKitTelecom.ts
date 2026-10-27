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
   * Whether the app supports video calls. When false, the CallKit call screen
   * hides its video button. Maps to CallKit's `CXProviderConfiguration.supportsVideo`.
   * Set it to false for audio-only apps.
   * @default true
   * @platform ios
   */
  supportsVideo?: boolean;
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
   * Path (relative to project root) to the small icon for the call
   * notifications on Android (incoming, dialing, ongoing, ended). Android draws
   * the small icon from the alpha channel only, so use a white glyph on a
   * transparent background, ideally 96x96 px. A `.png` or a vector drawable
   * `.xml` is accepted. When omitted, the launcher icon is used, which renders
   * as a solid shape for opaque icons.
   * @platform android
   */
  notificationIconAndroid?: string;
  /**
   * Text shown on the Android call notifications, the incoming-call screen,
   * and the notification channels in system settings, keyed by locale.
   * `default` replaces the built-in English text; any other key is a BCP 47
   * locale tag (`"es"`, `"pt-BR"`) and is used when the device is set to that
   * language. Android picks the locale at runtime. Unset keys fall back to
   * `default`, then to the built-in English text.
   *
   * Each string is a resource named `expo_callkit_telecom_<key in snake_case>`
   * (e.g. `incomingVideoCall` → `expo_callkit_telecom_incoming_video_call`),
   * so bare apps can also override them in their own `strings.xml` files.
   *
   * @example
   * {
   *   default: { incomingCall: "Incoming Acme call" },
   *   es: { incomingCall: "Llamada entrante", answer: "Contestar", decline: "Rechazar" },
   * }
   * @platform android
   */
  notificationStringsAndroid?: Record<string, AndroidNotificationStrings>;
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
};

/**
 * Text on the Android call notifications and incoming-call screen. See
 * {@link ExpoCallKitTelecomPluginProps.notificationStringsAndroid}.
 */
export type AndroidNotificationStrings = {
  /** Name of the incoming-call notification channel. @default "Incoming calls" */
  channelIncomingName?: string;
  /** Description of the incoming-call notification channel. @default "Notifications for incoming calls" */
  channelIncomingDescription?: string;
  /** Name of the ongoing-call notification channel. @default "Ongoing calls" */
  channelOngoingName?: string;
  /** Description of the ongoing-call notification channel. @default "Notifications for active calls" */
  channelOngoingDescription?: string;
  /** @default "Incoming call" */
  incomingCall?: string;
  /** @default "Incoming video call" */
  incomingVideoCall?: string;
  /** @default "Dialing..." */
  dialing?: string;
  /** @default "Ongoing call" */
  ongoingCall?: string;
  /** @default "Call ended" */
  callEnded?: string;
  /** Caller name shown when the caller has no name, number, or email. @default "Unknown" */
  unknownCaller?: string;
  /** Label of the answer button on the incoming-call screen. @default "Answer" */
  answer?: string;
  /** Label of the decline button on the incoming-call screen. @default "Decline" */
  decline?: string;
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
