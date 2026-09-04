import type { ExpoConfig } from "expo/config";

import type { ExpoCallKitTelecomPluginProps } from "../withExpoCallKitTelecom";
import { withExpoCallKitTelecomIos } from "../withExpoCallKitTelecomIos";

/**
 * Applies the iOS plugin to a bare config and executes the registered
 * Info.plist mod chain against an empty plist, returning the resulting
 * plist entries. This exercises every `withInfoPlist` mod the plugin
 * composes, without running a full prebuild.
 */
async function evaluateInfoPlist(
  props: ExpoCallKitTelecomPluginProps,
): Promise<Record<string, unknown>> {
  let config: ExpoConfig & { mods?: any } = {
    name: "test-app",
    slug: "test-app",
  };
  config = withExpoCallKitTelecomIos(config, props) as typeof config;

  const infoPlistMod = config.mods?.ios?.infoPlist;
  expect(typeof infoPlistMod).toBe("function");

  const result = await infoPlistMod({
    ...config,
    modResults: {},
    modRequest: {
      projectRoot: "/tmp/test-app",
      platformProjectRoot: "/tmp/test-app/ios",
      modName: "infoPlist",
      platform: "ios",
      introspect: true,
    },
    modRawConfig: { name: "test-app", slug: "test-app" },
  });
  return result.modResults;
}

describe("withExpoCallKitTelecomIos Info.plist configuration", () => {
  it("defaults ExpoCallKitTelecomIncludesCallsInRecents to true when the prop is omitted", async () => {
    const plist = await evaluateInfoPlist({});
    expect(plist.ExpoCallKitTelecomIncludesCallsInRecents).toBe(true);
  });

  it("writes ExpoCallKitTelecomIncludesCallsInRecents=false when the prop is false", async () => {
    const plist = await evaluateInfoPlist({ includesCallsInRecents: false });
    expect(plist.ExpoCallKitTelecomIncludesCallsInRecents).toBe(false);
  });

  it("writes ExpoCallKitTelecomIncludesCallsInRecents=true when the prop is explicitly true", async () => {
    const plist = await evaluateInfoPlist({ includesCallsInRecents: true });
    expect(plist.ExpoCallKitTelecomIncludesCallsInRecents).toBe(true);
  });

  it("keeps writing the timeout defaults alongside the new key (chain intact)", async () => {
    const plist = await evaluateInfoPlist({});
    expect(plist.ExpoCallKitTelecomIncomingCallTimeout).toBe(45);
    expect(plist.ExpoCallKitTelecomOutgoingCallTimeout).toBe(60);
    expect(plist.ExpoCallKitTelecomFulfillAnswerCallTimeout).toBe(30);
  });
});
