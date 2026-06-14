const { withDangerousMod } = require("expo/config-plugins");
const fs = require("node:fs");
const path = require("node:path");

/**
 * Copies the example call-event receiver (CallEndedReceiver.kt, next to this file) into the
 * generated android project, rewriting its package to match the app.
 *
 * The manifest `<receiver>` entry is NOT registered here — the expo-callkit-telecom config
 * plugin does that from its `androidEventReceiver` prop (see app.config.ts), so the broadcast
 * action string lives in one place. This plugin only exists because the in-repo example is a
 * managed app with no local native module to hold the source; a real app would put the receiver
 * in a local Expo module (or bare android/) and just set `androidEventReceiver`.
 *
 * @type {import("expo/config-plugins").ConfigPlugin}
 */
function withCallEndedReceiver(config) {
  return withDangerousMod(config, [
    "android",
    (config) => {
      const packageName = config.android?.package;
      if (!packageName) throw new Error("withCallEndedReceiver: android.package is not set");
      const packageDir = path.join(
        config.modRequest.platformProjectRoot,
        "app/src/main/java",
        ...packageName.split("."),
      );
      fs.mkdirSync(packageDir, { recursive: true });
      const source = fs
        .readFileSync(path.join(__dirname, "CallEndedReceiver.kt"), "utf8")
        .replace(/^package .*$/m, `package ${packageName}`);
      fs.writeFileSync(path.join(packageDir, "CallEndedReceiver.kt"), source);
      return config;
    },
  ]);
}

module.exports = withCallEndedReceiver;
