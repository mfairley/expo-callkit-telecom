const { AndroidConfig, withAndroidManifest, withDangerousMod } = require("expo/config-plugins");
const fs = require("node:fs");
const path = require("node:path");

const RECEIVER_NAME = ".CallEndedReceiver";
const ACTION_CALL_ENDED = "expo.modules.callkittelecom.ACTION_CALL_ENDED";

/**
 * Registers a manifest BroadcastReceiver for the module's call-ended broadcast (fired when an
 * incoming call ends and no live JS observer exists — see "Call ended while the app is killed"
 * in docs/platform-notes.md) and copies the receiver implementation (CallEndedReceiver.kt,
 * next to this file) into the generated android project.
 *
 * @type {import("expo/config-plugins").ConfigPlugin}
 */
function withCallEndedReceiver(config) {
  config = withAndroidManifest(config, (config) => {
    const app = AndroidConfig.Manifest.getMainApplicationOrThrow(config.modResults);
    app.receiver = (app.receiver ?? []).filter(
      (receiver) => receiver.$["android:name"] !== RECEIVER_NAME,
    );
    app.receiver.push({
      $: { "android:name": RECEIVER_NAME, "android:exported": "false" },
      "intent-filter": [
        { action: [{ $: { "android:name": ACTION_CALL_ENDED } }] },
      ],
    });
    return config;
  });

  config = withDangerousMod(config, [
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

  return config;
}

module.exports = withCallEndedReceiver;
