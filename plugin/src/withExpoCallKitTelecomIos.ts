import {
  type ConfigPlugin,
  IOSConfig,
  withEntitlementsPlist,
  withInfoPlist,
  withXcodeProject,
} from "expo/config-plugins";
import { copyFileSync, existsSync } from "fs";
import { basename, resolve } from "path";

import {
  DEFAULT_FULFILL_ANSWER_CALL_TIMEOUT,
  DEFAULT_INCOMING_CALL_TIMEOUT,
  DEFAULT_OUTGOING_CALL_TIMEOUT,
} from "./constants";
import type { ExpoCallKitTelecomPluginProps } from "./withExpoCallKitTelecom";

const ERROR_MSG_PREFIX = "An error occurred while configuring iOS calls. ";

// Default permission messages
const CAMERA_USAGE = "Allow $(PRODUCT_NAME) to access your camera";
const MICROPHONE_USAGE = "Allow $(PRODUCT_NAME) to access your microphone";

// Required background modes for CallKit and PushKit:
// - voip: Receive VoIP push notifications to wake the app for incoming calls
// - audio: Continue audio playback/recording during calls when app is backgrounded
const BACKGROUND_MODES = ["voip", "audio"];

// SiriKit intents for voice-activated calls
// INStartCallIntent: Unified intent for iOS 13+ (recommended)
// INStartAudioCallIntent/INStartVideoCallIntent: Deprecated in iOS 13, but still
// sent by the system in some cases (e.g., redialing from call history)
const SIRI_INTENTS = [
  "INStartCallIntent",
  "INStartAudioCallIntent",
  "INStartVideoCallIntent",
];

/**
 * Configures camera and microphone permissions for VoIP and video calls.
 */
const withPermissions: ConfigPlugin<{
  cameraPermission?: string | false;
  microphonePermission?: string | false;
}> = (config, { cameraPermission, microphonePermission }) => {
  return IOSConfig.Permissions.createPermissionsPlugin({
    NSCameraUsageDescription: CAMERA_USAGE,
    NSMicrophoneUsageDescription: MICROPHONE_USAGE,
  })(config, {
    NSCameraUsageDescription: cameraPermission,
    NSMicrophoneUsageDescription: microphonePermission,
  });
};

/**
 * Configures push notification entitlement for PushKit VoIP notifications.
 */
const withPushNotificationEntitlement: ConfigPlugin = (config) => {
  return withEntitlementsPlist(config, (config) => {
    const key = "aps-environment";
    // Only set if not already configured; production builds use provisioning profile value
    if (!config.modResults[key]) {
      config.modResults[key] = "development";
    }
    return config;
  });
};

/**
 * Configures UIBackgroundModes for VoIP call handling.
 */
const withBackgroundModes: ConfigPlugin = (config) => {
  return withInfoPlist(config, (config) => {
    const existingModes = config.modResults.UIBackgroundModes;
    const modes = Array.isArray(existingModes)
      ? (existingModes as string[])
      : [];

    const newModes = new Set([...modes, ...BACKGROUND_MODES]);
    config.modResults.UIBackgroundModes = [...newModes];

    return config;
  });
};

/**
 * Configures SiriKit intents for voice-activated audio/video calls.
 */
const withSiriIntents: ConfigPlugin = (config) => {
  return withInfoPlist(config, (config) => {
    const existingIntents = config.modResults.NSUserActivityTypes;
    const intents = Array.isArray(existingIntents)
      ? (existingIntents as string[])
      : [];

    const newIntents = new Set([...intents, ...SIRI_INTENTS]);
    config.modResults.NSUserActivityTypes = [...newIntents];

    return config;
  });
};

/**
 * Configures call timeout values in Info.plist.
 */
const withTimeouts: ConfigPlugin<{
  incomingCallTimeout?: number;
  outgoingCallTimeout?: number;
  fulfillAnswerCallTimeout?: number;
}> = (
  config,
  { incomingCallTimeout, outgoingCallTimeout, fulfillAnswerCallTimeout },
) => {
  return withInfoPlist(config, (config) => {
    config.modResults.ExpoCallKitTelecomIncomingCallTimeout =
      incomingCallTimeout ?? DEFAULT_INCOMING_CALL_TIMEOUT;
    config.modResults.ExpoCallKitTelecomOutgoingCallTimeout =
      outgoingCallTimeout ?? DEFAULT_OUTGOING_CALL_TIMEOUT;
    config.modResults.ExpoCallKitTelecomFulfillAnswerCallTimeout =
      fulfillAnswerCallTimeout ?? DEFAULT_FULFILL_ANSWER_CALL_TIMEOUT;
    return config;
  });
};

/**
 * Copies sound files into the iOS project bundle.
 */
function setSoundFiles(
  config: Parameters<Parameters<typeof withXcodeProject>[1]>[0],
  sounds: string[],
) {
  const projectRoot = config.modRequest.projectRoot;
  const projectName = config.modRequest.projectName;

  if (!projectName) {
    throw new Error(`${ERROR_MSG_PREFIX}Unable to find iOS project name.`);
  }

  const sourceRoot = resolve(projectRoot, "ios", projectName);

  for (const soundPath of sounds) {
    const filename = basename(soundPath);
    const sourcePath = resolve(projectRoot, soundPath);
    const destinationPath = resolve(sourceRoot, filename);

    if (!existsSync(sourcePath)) {
      throw new Error(`${ERROR_MSG_PREFIX}Sound file not found: ${sourcePath}`);
    }

    // Copy the file to the iOS project directory
    copyFileSync(sourcePath, destinationPath);

    // Add the file to the Xcode project if not already present
    if (!config.modResults.hasFile(`${projectName}/${filename}`)) {
      config.modResults = IOSConfig.XcodeUtils.addResourceFileToGroup({
        filepath: `${projectName}/${filename}`,
        groupName: projectName,
        isBuildFile: true,
        project: config.modResults,
      });
    }
  }

  return config;
}

/**
 * Copies sound files into the iOS project bundle.
 */
const withSounds: ConfigPlugin<{ sounds?: string[] }> = (
  config,
  { sounds },
) => {
  if (sounds && sounds.length > 0) {
    config = withXcodeProject(config, (config) => {
      return setSoundFiles(config, sounds);
    });
  }
  return config;
};

/**
 * Configures the default ringtone for incoming calls in Info.plist.
 */
const withDefaultRingtone: ConfigPlugin<{
  sounds?: string[];
  defaultRingtone?: string;
}> = (config, { sounds, defaultRingtone }) => {
  const soundFilenames = sounds?.map((s) => basename(s)) ?? [];

  // Validate defaultRingtone if specified and not 'default'
  if (defaultRingtone && defaultRingtone !== "default") {
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
  }

  return withInfoPlist(config, (config) => {
    config.modResults.ExpoCallKitTelecomDefaultRingtone = defaultRingtone || "default";
    return config;
  });
};

/**
 * Configures the default dialtone for outgoing calls in Info.plist.
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

  return withInfoPlist(config, (config) => {
    config.modResults.ExpoCallKitTelecomDefaultDialtone = defaultDialtone;
    return config;
  });
};

export const withExpoCallKitTelecomIos: ConfigPlugin<ExpoCallKitTelecomPluginProps> = (
  config,
  {
    cameraPermission,
    microphonePermission,
    incomingCallTimeout,
    outgoingCallTimeout,
    fulfillAnswerCallTimeout,
    sounds,
    defaultRingtoneIos,
    defaultDialtone,
  },
) => {
  config = withPermissions(config, { cameraPermission, microphonePermission });
  config = withPushNotificationEntitlement(config);
  config = withBackgroundModes(config);
  config = withSiriIntents(config);
  config = withTimeouts(config, {
    incomingCallTimeout,
    outgoingCallTimeout,
    fulfillAnswerCallTimeout,
  });
  config = withSounds(config, { sounds });
  config = withDefaultRingtone(config, {
    sounds,
    defaultRingtone: defaultRingtoneIos,
  });
  config = withDefaultDialtone(config, { sounds, defaultDialtone });

  return config;
};
