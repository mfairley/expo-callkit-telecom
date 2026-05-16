import {
  AndroidConfig,
  type ConfigPlugin,
  withAndroidManifest,
  withDangerousMod,
} from "expo/config-plugins";
import { copyFileSync, existsSync, mkdirSync } from "fs";
import { basename, resolve } from "path";

import {
  DEFAULT_FULFILL_ANSWER_CALL_TIMEOUT,
  DEFAULT_INCOMING_CALL_TIMEOUT,
  DEFAULT_OUTGOING_CALL_TIMEOUT,
} from "./constants";
import type { ExpoCallKitTelecomPluginProps } from "./withExpoCallKitTelecom";

const ERROR_MSG_PREFIX = "An error occurred while configuring Android calls. ";

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
  config = withFirebaseMessagingService(config, props);
  return config;
};
