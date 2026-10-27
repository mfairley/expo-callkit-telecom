import type { ExpoConfig } from "expo/config";
import { existsSync } from "node:fs";
import { join } from "node:path";

// Set these in .env.local to build with your own push credentials; see README.md.
const BUNDLE_ID = process.env.BUNDLE_ID || "com.example.expocallkittelecom";
const GOOGLE_SERVICES_FILE = process.env.GOOGLE_SERVICES_FILE || "./google-services.json";
// Firebase is optional: without the file the app builds and runs, minus FCM VoIP push.
const hasGoogleServicesFile = existsSync(join(__dirname, GOOGLE_SERVICES_FILE));

const config: ExpoConfig = {
  name: "CallKit Tel",
  slug: "expo-callkit-telecom-example",
  version: "1.0.0",
  orientation: "portrait",
  icon: "./assets/icon.png",
  ios: {
    supportsTablet: true,
    bundleIdentifier: BUNDLE_ID,
    icon: "./assets/app-icon.icon",
  },
  android: {
    adaptiveIcon: {
      foregroundImage: "./assets/adaptive-icon.png",
      backgroundImage: "./assets/adaptive-icon.png",
      monochromeImage: "./assets/adaptive-icon.png",
    },
    predictiveBackGestureEnabled: false,
    package: BUNDLE_ID,
    googleServicesFile: hasGoogleServicesFile ? GOOGLE_SERVICES_FILE : undefined,
  },
  web: {
    favicon: "./assets/favicon.png",
  },
  plugins: [
    [
      "expo-splash-screen",
      {
        image: "./assets/splash-icon.png",
        resizeMode: "contain",
        backgroundColor: "#ffffff",
      },
    ],
    [
      "expo-build-properties",
      {
        ios: { deploymentTarget: "16.4" },
        android: { minSdkVersion: 26 },
      },
    ],
    [
      require("../../app.plugin.js").default,
      {
        sounds: [
          "./assets/sounds/ringtone.wav",
          "./assets/sounds/dialtone.wav",
        ],
        defaultRingtoneIos: "ringtone.wav",
        defaultRingtoneAndroid: "ringtone.wav",
        defaultDialtone: "dialtone.wav",
        iconTemplateIos: "./assets/callkit-icon.png",
        androidEventReceiver: ".CallEndedReceiver",
      },
    ],
    // Copies CallEndedReceiver.kt into the android project; the manifest <receiver>
    // entry itself is registered by the plugin above via androidEventReceiver.
    "./plugins/withCallEndedReceiver",
  ],
};

export default config;
