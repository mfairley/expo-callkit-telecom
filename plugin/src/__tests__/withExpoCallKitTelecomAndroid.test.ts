import type { ExpoConfig } from "expo/config";
import type { AndroidConfig } from "expo/config-plugins";

import type { ExpoCallKitTelecomPluginProps } from "../withExpoCallKitTelecom";
import { withExpoCallKitTelecomAndroid } from "../withExpoCallKitTelecomAndroid";

const ANSWER_ON_LOCK_SCREEN_KEY = "ExpoCallKitTelecomAnswerOnLockScreen";

function bareManifest(): AndroidConfig.Manifest.AndroidManifest {
  return {
    manifest: {
      $: { "xmlns:android": "http://schemas.android.com/apk/res/android" },
      application: [{ $: { "android:name": ".MainApplication" } }],
    },
  };
}

/**
 * Applies the Android plugin to a bare config and executes the registered
 * AndroidManifest mod chain against the given manifest, returning the result.
 * This exercises every `withAndroidManifest` mod the plugin composes, without
 * running a full prebuild.
 */
async function evaluateManifest(
  props: ExpoCallKitTelecomPluginProps,
  manifest: AndroidConfig.Manifest.AndroidManifest = bareManifest(),
): Promise<AndroidConfig.Manifest.AndroidManifest> {
  let config: ExpoConfig & { mods?: any } = {
    name: "test-app",
    slug: "test-app",
  };
  config = withExpoCallKitTelecomAndroid(config, props) as typeof config;

  const manifestMod = config.mods?.android?.manifest;
  expect(typeof manifestMod).toBe("function");

  const result = await manifestMod({
    ...config,
    modResults: manifest,
    modRequest: {
      projectRoot: "/tmp/test-app",
      platformProjectRoot: "/tmp/test-app/android",
      modName: "manifest",
      platform: "android",
      introspect: true,
    },
    modRawConfig: { name: "test-app", slug: "test-app" },
  });
  return result.modResults;
}

function metaDataValues(
  manifest: AndroidConfig.Manifest.AndroidManifest,
  key: string,
): string[] {
  const items = manifest.manifest.application?.[0]["meta-data"] ?? [];
  return items
    .filter((item) => item.$["android:name"] === key)
    .map((item) => item.$["android:value"] as string);
}

describe("withExpoCallKitTelecomAndroid answer on lock screen", () => {
  it("writes ExpoCallKitTelecomAnswerOnLockScreen=false when the prop is omitted", async () => {
    const manifest = await evaluateManifest({});
    expect(metaDataValues(manifest, ANSWER_ON_LOCK_SCREEN_KEY)).toEqual([
      "false",
    ]);
  });

  it("writes ExpoCallKitTelecomAnswerOnLockScreen=false when the prop is false", async () => {
    const manifest = await evaluateManifest({
      androidAnswerOnLockScreen: false,
    });
    expect(metaDataValues(manifest, ANSWER_ON_LOCK_SCREEN_KEY)).toEqual([
      "false",
    ]);
  });

  it("writes ExpoCallKitTelecomAnswerOnLockScreen=true when the prop is true", async () => {
    const manifest = await evaluateManifest({
      androidAnswerOnLockScreen: true,
    });
    expect(metaDataValues(manifest, ANSWER_ON_LOCK_SCREEN_KEY)).toEqual([
      "true",
    ]);
  });

  it("keeps a single entry across repeated prebuilds", async () => {
    const props = { androidAnswerOnLockScreen: true };
    const first = await evaluateManifest(props);
    const second = await evaluateManifest(props, first);
    expect(metaDataValues(second, ANSWER_ON_LOCK_SCREEN_KEY)).toEqual(["true"]);
  });

  it("flips a stale true to false on a non-clean prebuild after the prop is turned off", async () => {
    const enabled = await evaluateManifest({ androidAnswerOnLockScreen: true });
    const disabled = await evaluateManifest({}, enabled);
    expect(metaDataValues(disabled, ANSWER_ON_LOCK_SCREEN_KEY)).toEqual([
      "false",
    ]);
  });

  it("adds WAKE_LOCK once when the prop is true, also across repeated prebuilds", async () => {
    const props = { androidAnswerOnLockScreen: true };
    const first = await evaluateManifest(props);
    const second = await evaluateManifest(props, first);
    const permissions = (second.manifest["uses-permission"] ?? []).map(
      (item) => item.$["android:name"],
    );
    expect(permissions).toEqual(["android.permission.WAKE_LOCK"]);
  });

  it("does not add WAKE_LOCK when the prop is omitted or false", async () => {
    const omitted = await evaluateManifest({});
    const disabled = await evaluateManifest({
      androidAnswerOnLockScreen: false,
    });
    expect(omitted.manifest["uses-permission"] ?? []).toEqual([]);
    expect(disabled.manifest["uses-permission"] ?? []).toEqual([]);
  });

  it("keeps writing the timeout defaults alongside the new key (chain intact)", async () => {
    const manifest = await evaluateManifest({
      androidAnswerOnLockScreen: true,
    });
    expect(
      metaDataValues(manifest, "ExpoCallKitTelecomIncomingCallTimeout"),
    ).toEqual(["45"]);
    expect(
      metaDataValues(manifest, "ExpoCallKitTelecomOutgoingCallTimeout"),
    ).toEqual(["60"]);
    expect(
      metaDataValues(manifest, "ExpoCallKitTelecomFulfillAnswerCallTimeout"),
    ).toEqual(["30"]);
  });
});
