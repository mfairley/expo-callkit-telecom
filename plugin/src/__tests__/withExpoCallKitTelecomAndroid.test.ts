import type { ExpoConfig } from "expo/config";
import { existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "fs";
import { tmpdir } from "os";
import { join } from "path";

import type { ExpoCallKitTelecomPluginProps } from "../withExpoCallKitTelecom";
import {
  toAndroidValuesDirectory,
  toNotificationStringResourceName,
  withExpoCallKitTelecomAndroid,
} from "../withExpoCallKitTelecomAndroid";

/**
 * Applies the Android plugin to a bare config and executes the registered
 * mod named `modName` against `modResults`, returning the result.
 */
async function evaluateMod(
  props: ExpoCallKitTelecomPluginProps,
  modName: "manifest" | "strings",
  modResults: unknown,
): Promise<any> {
  let config: ExpoConfig & { mods?: any } = {
    name: "test-app",
    slug: "test-app",
  };
  config = withExpoCallKitTelecomAndroid(config, props) as typeof config;

  const mod = config.mods?.android?.[modName];
  if (!mod) {
    return undefined;
  }

  const result = await mod({
    ...config,
    modResults,
    modRequest: {
      projectRoot: "/tmp/test-app",
      platformProjectRoot: "/tmp/test-app/android",
      modName,
      platform: "android",
      introspect: true,
    },
    modRawConfig: { name: "test-app", slug: "test-app" },
  });
  return result.modResults;
}

/**
 * Runs the plugin's dangerous mods against a temporary project directory and
 * returns its root, so tests can inspect the files the mods wrote.
 */
async function runDangerousMods(
  props: ExpoCallKitTelecomPluginProps,
  projectRoot: string,
): Promise<void> {
  let config: ExpoConfig & { mods?: any } = {
    name: "test-app",
    slug: "test-app",
  };
  config = withExpoCallKitTelecomAndroid(config, props) as typeof config;

  await config.mods.android.dangerous({
    ...config,
    modResults: {},
    modRequest: {
      projectRoot,
      platformProjectRoot: join(projectRoot, "android"),
      modName: "dangerous",
      platform: "android",
      introspect: false,
    },
    modRawConfig: { name: "test-app", slug: "test-app" },
  });
}

const emptyManifest = () => ({
  manifest: { $: {}, application: [{ $: { "android:name": ".MainApplication" } }] },
});

describe("toNotificationStringResourceName", () => {
  it("converts camelCase keys to the library's snake_case resource names", () => {
    expect(toNotificationStringResourceName("incomingVideoCall")).toBe(
      "expo_callkit_telecom_incoming_video_call",
    );
    expect(toNotificationStringResourceName("answer")).toBe(
      "expo_callkit_telecom_answer",
    );
  });
});

describe("toAndroidValuesDirectory", () => {
  it("maps a bare language to values-<lang>", () => {
    expect(toAndroidValuesDirectory("es")).toBe("values-es");
    expect(toAndroidValuesDirectory("FR")).toBe("values-fr");
  });

  it("maps tags with a region or script to the b+ form", () => {
    expect(toAndroidValuesDirectory("pt-BR")).toBe("values-b+pt+BR");
    expect(toAndroidValuesDirectory("zh-Hant-TW")).toBe("values-b+zh+Hant+TW");
  });

  it("rejects strings that aren't BCP 47 tags", () => {
    expect(() => toAndroidValuesDirectory("pt_BR")).toThrow(/BCP 47/);
    expect(() => toAndroidValuesDirectory("")).toThrow(/BCP 47/);
  });
});

describe("withExpoCallKitTelecomAndroid notification strings", () => {
  it("writes default overrides into strings.xml under the library's names", async () => {
    const strings = await evaluateMod(
      {
        notificationStringsAndroid: {
          default: { incomingCall: "Incoming Acme call", answer: "Pick up" },
        },
      },
      "strings",
      { resources: {} },
    );
    expect(strings.resources.string).toEqual(
      expect.arrayContaining([
        { $: { name: "expo_callkit_telecom_incoming_call" }, _: "Incoming Acme call" },
        { $: { name: "expo_callkit_telecom_answer" }, _: "Pick up" },
      ]),
    );
  });

  it("registers no strings mod when there are no default overrides", async () => {
    const strings = await evaluateMod(
      { notificationStringsAndroid: { es: { answer: "Contestar" } } },
      "strings",
      { resources: {} },
    );
    expect(strings).toBeUndefined();
  });

  it("writes each locale into its own values-<qualifier>/strings.xml, escaped", async () => {
    const projectRoot = mkdtempSync(join(tmpdir(), "callkit-telecom-strings-"));
    try {
      await runDangerousMods(
        {
          notificationStringsAndroid: {
            es: { answer: "Contestar" },
            "pt-BR": { decline: "Recusar", callEnded: "Chamada d'encerrada" },
          },
        },
        projectRoot,
      );
      const res = join(projectRoot, "android", "app", "src", "main", "res");
      expect(readFileSync(join(res, "values-es", "strings.xml"), "utf8")).toContain(
        '<string name="expo_callkit_telecom_answer">Contestar</string>',
      );
      const ptBR = readFileSync(join(res, "values-b+pt+BR", "strings.xml"), "utf8");
      expect(ptBR).toContain(
        '<string name="expo_callkit_telecom_decline">Recusar</string>',
      );
      expect(ptBR).toContain(
        `<string name="expo_callkit_telecom_call_ended">Chamada d\\'encerrada</string>`,
      );
    } finally {
      rmSync(projectRoot, { recursive: true, force: true });
    }
  });

  it("rejects an invalid locale key at plugin time", () => {
    expect(() =>
      withExpoCallKitTelecomAndroid(
        { name: "test-app", slug: "test-app" },
        { notificationStringsAndroid: { pt_BR: { answer: "Atender" } } },
      ),
    ).toThrow(/BCP 47/);
  });
});

describe("withExpoCallKitTelecomAndroid notification icon", () => {
  it("points ExpoCallKitTelecomNotificationIcon at the copied drawable", async () => {
    const manifest = await evaluateMod(
      { notificationIconAndroid: "./assets/notification-icon.png" },
      "manifest",
      emptyManifest(),
    );
    expect(manifest.manifest.application[0]["meta-data"]).toEqual(
      expect.arrayContaining([
        {
          $: {
            "android:name": "ExpoCallKitTelecomNotificationIcon",
            "android:resource": "@drawable/expo_callkit_telecom_notification_icon",
          },
        },
      ]),
    );
  });

  it("copies the icon into res/drawable, replacing a copy with the other extension", async () => {
    const projectRoot = mkdtempSync(join(tmpdir(), "callkit-telecom-icon-"));
    try {
      writeFileSync(join(projectRoot, "icon.xml"), "<vector />");
      writeFileSync(join(projectRoot, "icon.png"), "png-bytes");
      const drawable = join(projectRoot, "android", "app", "src", "main", "res", "drawable");

      await runDangerousMods({ notificationIconAndroid: "./icon.xml" }, projectRoot);
      expect(existsSync(join(drawable, "expo_callkit_telecom_notification_icon.xml"))).toBe(true);

      await runDangerousMods({ notificationIconAndroid: "./icon.png" }, projectRoot);
      expect(
        readFileSync(join(drawable, "expo_callkit_telecom_notification_icon.png"), "utf8"),
      ).toBe("png-bytes");
      expect(existsSync(join(drawable, "expo_callkit_telecom_notification_icon.xml"))).toBe(false);
    } finally {
      rmSync(projectRoot, { recursive: true, force: true });
    }
  });

  it("rejects file types Android can't use as a drawable", () => {
    expect(() =>
      withExpoCallKitTelecomAndroid(
        { name: "test-app", slug: "test-app" },
        { notificationIconAndroid: "./assets/icon.jpg" },
      ),
    ).toThrow(/\.png or a vector drawable/);
  });
});
