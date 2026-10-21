import type { ExpoConfig } from "expo/config";
import type { AndroidConfig } from "expo/config-plugins";

import type { ExpoCallKitTelecomPluginProps } from "../withExpoCallKitTelecom";
import { withExpoCallKitTelecomAndroid } from "../withExpoCallKitTelecomAndroid";

const MODULE_SERVICE =
  "expo.modules.callkittelecom.services.ExpoCallKitTelecomMessagingService";
const NOTIFICATIONS_SERVICE =
  "expo.modules.notifications.service.ExpoFirebaseMessagingService";

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

function services(manifest: AndroidConfig.Manifest.AndroidManifest) {
  return manifest.manifest.application?.[0].service ?? [];
}

function serviceNamed(
  manifest: AndroidConfig.Manifest.AndroidManifest,
  name: string,
) {
  const matches = services(manifest).filter(
    (service) => service.$["android:name"] === name,
  );
  expect(matches).toHaveLength(1);
  return matches[0];
}

describe("withExpoCallKitTelecomAndroid messaging service", () => {
  it("only removes expo-notifications' service when androidMessagingService is omitted", async () => {
    const manifest = await evaluateManifest({});

    expect(
      services(manifest).map((service) => service.$["android:name"]),
    ).toEqual([NOTIFICATIONS_SERVICE]);
    expect(serviceNamed(manifest, NOTIFICATIONS_SERVICE).$["tools:node"]).toBe(
      "remove",
    );
    expect(manifest.manifest.$["xmlns:tools"]).toBe(
      "http://schemas.android.com/tools",
    );
  });

  it("registers the subclass as a non-exported MESSAGING_EVENT service and removes the module's own", async () => {
    const manifest = await evaluateManifest({
      androidMessagingService: ".CallMessagingService",
    });

    const subclass = serviceNamed(manifest, ".CallMessagingService");
    expect(subclass.$["android:exported"]).toBe("false");
    expect(subclass.$["tools:node"]).toBeUndefined();
    expect(subclass["intent-filter"]).toEqual([
      {
        action: [
          { $: { "android:name": "com.google.firebase.MESSAGING_EVENT" } },
        ],
      },
    ]);

    expect(serviceNamed(manifest, MODULE_SERVICE).$["tools:node"]).toBe(
      "remove",
    );
    expect(serviceNamed(manifest, NOTIFICATIONS_SERVICE).$["tools:node"]).toBe(
      "remove",
    );
  });

  it("is idempotent across repeated prebuilds", async () => {
    const props = { androidMessagingService: ".CallMessagingService" };
    const first = await evaluateManifest(props);
    const snapshot = JSON.parse(JSON.stringify(services(first)));

    const second = await evaluateManifest(props, first);

    expect(services(second)).toEqual(snapshot);
  });
});
