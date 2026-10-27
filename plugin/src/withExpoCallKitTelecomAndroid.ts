import {
  AndroidConfig,
  type ConfigPlugin,
  withAndroidManifest,
  withDangerousMod,
  withStringsXml,
  XML,
} from "expo/config-plugins";
import { copyFileSync, existsSync, mkdirSync, rmSync } from "fs";
import { basename, extname, resolve } from "path";

import {
  ANDROID_CALL_EVENT_ACTION,
  DEFAULT_FULFILL_ANSWER_CALL_TIMEOUT,
  DEFAULT_INCOMING_CALL_TIMEOUT,
  DEFAULT_OUTGOING_CALL_TIMEOUT,
} from "./constants";
import type {
  AndroidNotificationStrings,
  ExpoCallKitTelecomPluginProps,
} from "./withExpoCallKitTelecom";

const ERROR_MSG_PREFIX = "An error occurred while configuring Android calls. ";

// Drawable name the `notificationIconAndroid` file is copied to
const NOTIFICATION_ICON_RESOURCE = "expo_callkit_telecom_notification_icon";
const NOTIFICATION_ICON_EXTENSIONS = [".png", ".xml"];

// biome-ignore lint/suspicious/noExplicitAny: Expo config plugin manifest types are untyped
function setMetaDataValue(app: any, key: string, value: string): void {
  const existing = app["meta-data"]?.find(
    // biome-ignore lint/suspicious/noExplicitAny: manifest meta-data items are untyped
    (item: any) => item.$["android:name"] === key,
  );
  if (existing) {
    existing.$["android:value"] = value;
    return;
  }

  if (!app["meta-data"]) {
    app["meta-data"] = [];
  }
  app["meta-data"].push({
    $: {
      "android:name": key,
      "android:value": value,
    },
  });
}

/**
 * Sanitizes a filename for use as an Android raw resource name.
 *
 * Android raw resource names must be lowercase, alphanumeric + underscores,
 * and cannot start with a digit.
 */
function toAndroidRawResourceName(filename: string): string {
  const withoutExtension = filename.replace(/\.[^.]+$/, "");
  const sanitized = withoutExtension.toLowerCase().replace(/[^a-z0-9_]/g, "_");
  return /^\d/.test(sanitized) ? `_${sanitized}` : sanitized;
}

/**
 * Configures call timeout values in AndroidManifest metadata.
 */
const withTimeouts: ConfigPlugin<{
  incomingCallTimeout?: number;
  outgoingCallTimeout?: number;
  fulfillAnswerCallTimeout?: number;
}> = (
  config,
  { incomingCallTimeout, outgoingCallTimeout, fulfillAnswerCallTimeout },
) => {
  return withAndroidManifest(config, (config) => {
    const app = AndroidConfig.Manifest.getMainApplicationOrThrow(
      config.modResults,
    );

    setMetaDataValue(
      app,
      "ExpoCallKitTelecomIncomingCallTimeout",
      String(incomingCallTimeout ?? DEFAULT_INCOMING_CALL_TIMEOUT),
    );

    setMetaDataValue(
      app,
      "ExpoCallKitTelecomOutgoingCallTimeout",
      String(outgoingCallTimeout ?? DEFAULT_OUTGOING_CALL_TIMEOUT),
    );

    setMetaDataValue(
      app,
      "ExpoCallKitTelecomFulfillAnswerCallTimeout",
      String(fulfillAnswerCallTimeout ?? DEFAULT_FULFILL_ANSWER_CALL_TIMEOUT),
    );

    return config;
  });
};

/**
 * Copies sound files into the Android raw resources directory.
 */
const withSounds: ConfigPlugin<{ sounds?: string[] }> = (
  config,
  { sounds },
) => {
  if (!sounds || sounds.length === 0) {
    return config;
  }

  return withDangerousMod(config, [
    "android",
    (config) => {
      const projectRoot = config.modRequest.projectRoot;
      const rawDir = resolve(
        projectRoot,
        "android",
        "app",
        "src",
        "main",
        "res",
        "raw",
      );

      mkdirSync(rawDir, { recursive: true });

      for (const soundPath of sounds) {
        const filename = basename(soundPath);
        const sourcePath = resolve(projectRoot, soundPath);

        if (!existsSync(sourcePath)) {
          throw new Error(
            `${ERROR_MSG_PREFIX}Sound file not found: ${sourcePath}`,
          );
        }

        const resourceName = toAndroidRawResourceName(filename);
        const extension = filename.includes(".")
          ? filename.substring(filename.lastIndexOf("."))
          : "";
        const destinationPath = resolve(rawDir, `${resourceName}${extension}`);

        copyFileSync(sourcePath, destinationPath);
      }

      return config;
    },
  ]);
};

/**
 * Configures the default dialtone for outgoing calls in AndroidManifest metadata.
 */
const withDefaultDialtone: ConfigPlugin<{
  sounds?: string[];
  defaultDialtone?: string;
}> = (config, { sounds, defaultDialtone }) => {
  if (!defaultDialtone) {
    return config;
  }

  const soundFilenames = sounds?.map((s) => basename(s)) ?? [];

  if (soundFilenames.length === 0) {
    throw new Error(
      `${ERROR_MSG_PREFIX}"defaultDialtone" was specified but no ` +
        `sounds were provided.`,
    );
  }
  if (!soundFilenames.includes(defaultDialtone)) {
    throw new Error(
      `${ERROR_MSG_PREFIX}"defaultDialtone" must be one of the provided ` +
        `sounds (${soundFilenames.join(", ")}).`,
    );
  }

  return withAndroidManifest(config, (config) => {
    const app = AndroidConfig.Manifest.getMainApplicationOrThrow(
      config.modResults,
    );

    setMetaDataValue(
      app,
      "ExpoCallKitTelecomDefaultDialtone",
      toAndroidRawResourceName(defaultDialtone),
    );

    return config;
  });
};

/**
 * Configures the default ringtone for incoming calls in AndroidManifest metadata.
 *
 * Mirrors the iOS `withDefaultRingtone` plugin. The Kotlin side reads
 * `ExpoCallKitTelecomDefaultRingtone` from manifest metadata and sets it as the
 * notification channel sound.
 */
const withDefaultRingtone: ConfigPlugin<{
  sounds?: string[];
  defaultRingtone?: string;
}> = (config, { sounds, defaultRingtone }) => {
  if (!defaultRingtone || defaultRingtone === "default") {
    return config;
  }

  const soundFilenames = sounds?.map((s) => basename(s)) ?? [];

  if (soundFilenames.length === 0) {
    throw new Error(
      `${ERROR_MSG_PREFIX}"defaultRingtone" was specified but no ` +
        `sounds were provided.`,
    );
  }
  if (!soundFilenames.includes(defaultRingtone)) {
    throw new Error(
      `${ERROR_MSG_PREFIX}"defaultRingtone" must be one of the provided ` +
        `sounds (${soundFilenames.join(", ")}) or "default" for ` +
        `system ringtone.`,
    );
  }

  return withAndroidManifest(config, (config) => {
    const app = AndroidConfig.Manifest.getMainApplicationOrThrow(
      config.modResults,
    );

    setMetaDataValue(
      app,
      "ExpoCallKitTelecomDefaultRingtone",
      toAndroidRawResourceName(defaultRingtone),
    );

    return config;
  });
};

/**
 * Copies the notification small icon into the app's drawables and points the
 * `ExpoCallKitTelecomNotificationIcon` manifest metadata at it.
 */
const withNotificationIcon: ConfigPlugin<{ notificationIconAndroid?: string }> = (
  config,
  { notificationIconAndroid },
) => {
  if (!notificationIconAndroid) {
    return config;
  }

  const extension = extname(notificationIconAndroid).toLowerCase();
  if (!NOTIFICATION_ICON_EXTENSIONS.includes(extension)) {
    throw new Error(
      `${ERROR_MSG_PREFIX}"notificationIconAndroid" must be a .png or a ` +
        `vector drawable .xml, got: ${notificationIconAndroid}`,
    );
  }

  config = withDangerousMod(config, [
    "android",
    (config) => {
      const sourcePath = resolve(
        config.modRequest.projectRoot,
        notificationIconAndroid,
      );

      if (!existsSync(sourcePath)) {
        throw new Error(
          `${ERROR_MSG_PREFIX}Notification icon file not found: ${sourcePath}`,
        );
      }

      const drawableDir = resolve(
        config.modRequest.platformProjectRoot,
        "app",
        "src",
        "main",
        "res",
        "drawable",
      );
      mkdirSync(drawableDir, { recursive: true });

      // A leftover copy with the other extension (from an earlier prebuild)
      // would be a duplicate resource and fail the build.
      for (const ext of NOTIFICATION_ICON_EXTENSIONS) {
        rmSync(resolve(drawableDir, `${NOTIFICATION_ICON_RESOURCE}${ext}`), {
          force: true,
        });
      }
      copyFileSync(
        sourcePath,
        resolve(drawableDir, `${NOTIFICATION_ICON_RESOURCE}${extension}`),
      );

      return config;
    },
  ]);

  return withAndroidManifest(config, (config) => {
    const app = AndroidConfig.Manifest.getMainApplicationOrThrow(
      config.modResults,
    );

    AndroidConfig.Manifest.addMetaDataItemToMainApplication(
      app,
      "ExpoCallKitTelecomNotificationIcon",
      `@drawable/${NOTIFICATION_ICON_RESOURCE}`,
      "resource",
    );

    return config;
  });
};

/**
 * Converts a {@link AndroidNotificationStrings} key to the string resource
 * name the library declares, e.g. `incomingVideoCall` →
 * `expo_callkit_telecom_incoming_video_call`.
 */
export function toNotificationStringResourceName(key: string): string {
  return `expo_callkit_telecom_${key.replace(/[A-Z]/g, (c) => `_${c.toLowerCase()}`)}`;
}

/**
 * Converts a BCP 47 locale tag to an Android resource directory qualifier:
 * `es` → `values-es`, `pt-BR` → `values-b+pt+BR`. The `b+` form handles
 * scripts and regions alike and is supported on every API level this module
 * targets.
 */
export function toAndroidValuesDirectory(locale: string): string {
  if (!/^[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*$/.test(locale)) {
    throw new Error(
      `${ERROR_MSG_PREFIX}"notificationStringsAndroid" locale "${locale}" ` +
        `is not a BCP 47 tag (e.g. "es", "pt-BR").`,
    );
  }
  const subtags = locale.split("-");
  return subtags.length === 1
    ? `values-${locale.toLowerCase()}`
    : `values-b+${subtags.join("+")}`;
}

function toStringResourceItems(strings: AndroidNotificationStrings) {
  return Object.entries(strings)
    .filter((entry): entry is [string, string] => typeof entry[1] === "string")
    .map(([key, value]) =>
      AndroidConfig.Resources.buildResourceItem({
        name: toNotificationStringResourceName(key),
        value,
      }),
    );
}

/**
 * Writes `notificationStringsAndroid` overrides into the app's resources:
 * `default` into `values/strings.xml`, each locale into its own
 * `values-<qualifier>/strings.xml`. The app's resources win over the
 * library's at resource merge, and Android picks the locale at runtime.
 */
const withNotificationStrings: ConfigPlugin<{
  notificationStringsAndroid?: Record<string, AndroidNotificationStrings>;
}> = (config, { notificationStringsAndroid }) => {
  const { default: defaultStrings, ...localeStrings } =
    notificationStringsAndroid ?? {};

  const defaultItems = toStringResourceItems(defaultStrings ?? {});
  if (defaultItems.length > 0) {
    config = withStringsXml(config, (config) => {
      config.modResults = AndroidConfig.Strings.setStringItem(
        defaultItems,
        config.modResults,
      );
      return config;
    });
  }

  const locales = Object.entries(localeStrings).map(
    ([locale, strings]) =>
      [toAndroidValuesDirectory(locale), toStringResourceItems(strings)] as const,
  );
  if (locales.length === 0) {
    return config;
  }

  return withDangerousMod(config, [
    "android",
    async (config) => {
      const resDir = resolve(
        config.modRequest.platformProjectRoot,
        "app",
        "src",
        "main",
        "res",
      );

      for (const [valuesDirectory, items] of locales) {
        const path = resolve(resDir, valuesDirectory, "strings.xml");
        const xml = await AndroidConfig.Resources.readResourcesXMLAsync({ path });
        await XML.writeXMLAsync({
          path,
          xml: AndroidConfig.Strings.setStringItem(items, xml),
        });
      }

      return config;
    },
  ]);
};

/**
 * Removes expo-notifications' ExpoFirebaseMessagingService from the manifest.
 *
 * Our ExpoCallKitTelecomMessagingService extends it and takes over as the sole
 * MESSAGING_EVENT handler, delegating non-call messages via super.
 * Having both services registered would cause undefined delivery behaviour.
 *
 * The service is declared in expo-notifications' library AndroidManifest.xml,
 * so we must use `tools:node="remove"` to tell the manifest merger to strip it.
 */
const withFirebaseMessagingService: ConfigPlugin<ExpoCallKitTelecomPluginProps> = (
  config,
) => {
  return withAndroidManifest(config, (config) => {
    const manifest = config.modResults.manifest;

    // Ensure the tools namespace is declared on the root <manifest> element.
    if (!manifest.$["xmlns:tools"]) {
      manifest.$["xmlns:tools"] = "http://schemas.android.com/tools";
    }

    const app = AndroidConfig.Manifest.getMainApplicationOrThrow(
      config.modResults,
    );

    if (!app.service) {
      app.service = [];
    }

    const notificationsService =
      "expo.modules.notifications.service.ExpoFirebaseMessagingService";

    // Remove any existing entry first (idempotent across repeated prebuilds).
    app.service = app.service.filter(
      (service) => service.$?.["android:name"] !== notificationsService,
    );

    // Add a tools:node="remove" marker so the manifest merger strips the
    // library-declared service during the Gradle build.
    const removeEntry = {
      $: {
        "android:name": notificationsService,
        "tools:node": "remove",
      },
    };
    app.service.push(removeEntry);

    return config;
  });
};

/**
 * Registers a manifest BroadcastReceiver for the module's call-event broadcast.
 *
 * Fired when a call event can't reach a live JS observer (e.g. a killed-app
 * decline). The receiver entry uses {@link ANDROID_CALL_EVENT_ACTION}. The app
 * supplies the receiver class itself (the plugin does not generate it).
 */
const withEventReceiver: ConfigPlugin<{ androidEventReceiver?: string }> = (
  config,
  { androidEventReceiver },
) => {
  if (!androidEventReceiver) {
    return config;
  }

  return withAndroidManifest(config, (config) => {
    const app = AndroidConfig.Manifest.getMainApplicationOrThrow(
      config.modResults,
    );

    // Remove any existing entry first (idempotent across repeated prebuilds).
    app.receiver = (app.receiver ?? []).filter(
      (receiver) => receiver.$["android:name"] !== androidEventReceiver,
    );

    app.receiver.push({
      $: {
        "android:name": androidEventReceiver,
        "android:exported": "false",
      },
      "intent-filter": [
        {
          action: [{ $: { "android:name": ANDROID_CALL_EVENT_ACTION } }],
        },
      ],
    });

    return config;
  });
};

export const withExpoCallKitTelecomAndroid: ConfigPlugin<ExpoCallKitTelecomPluginProps> = (
  config,
  props,
) => {
  config = withTimeouts(config, props);
  config = withSounds(config, props);
  config = withDefaultRingtone(config, {
    sounds: props.sounds,
    defaultRingtone: props.defaultRingtoneAndroid,
  });
  config = withDefaultDialtone(config, props);
  config = withNotificationIcon(config, props);
  config = withNotificationStrings(config, props);
  config = withFirebaseMessagingService(config, props);
  config = withEventReceiver(config, props);
  return config;
};
