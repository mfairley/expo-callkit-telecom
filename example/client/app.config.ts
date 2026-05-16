import type { ExpoConfig } from "expo/config";

const BUNDLE_ID = "com.example.expocallkittelecom";

const config: ExpoConfig = {
  name: "expo-callkit-telecom-example",
  slug: "expo-callkit-telecom-example",
  version: "1.0.0",
  orientation: "portrait",
  icon: "./assets/icon.png",
  splash: {
    image: "./assets/splash-icon.png",
    resizeMode: "contain",
    backgroundColor: "#ffffff",
  },
  ios: {
    supportsTablet: true,
    bundleIdentifier: BUNDLE_ID,
  },
  android: {
    adaptiveIcon: {
      foregroundImage: "./assets/adaptive-icon.png",
      backgroundColor: "#ffffff",
    },
    predictiveBackGestureEnabled: false,
    package: BUNDLE_ID,
    googleServicesFile: "./google-services.json",
  },
  web: {
    favicon: "./assets/favicon.png",
  },
  plugins: [
    [
      "expo-build-properties",
      {
        ios: { deploymentTarget: "16.0" },
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
      },
    ],
  ],
};

export default config;
