#!/usr/bin/env bun
/**
 * Send a test VoIP push that wakes the module's native parser end-to-end.
 *
 * Reads APNs and FCM keys from on-disk files in this directory
 * (apns_key.p8, fcm_key.json). Auto-detects iOS (APNs VoIP) vs Android (FCM)
 * from which device-token env vars are set; pass `--ios` or `--android` to
 * force one.
 *
 * Usage:
 *   bun send-test-push.ts                  # auto, both if both set
 *   bun send-test-push.ts --ios            # iOS only
 *   bun send-test-push.ts --android        # Android only
 *   bun send-test-push.ts --display "Alice"
 *   bun send-test-push.ts --phoneNumber +14155551234   # E.164; lets the OS match a contact
 *   bun send-test-push.ts --video
 *   bun send-test-push.ts --production     # iOS prod APNs (default: sandbox)
 *
 * Required env (.env auto-loaded by bun):
 *   BUNDLE_ID            iOS bundle id of the client (must match
 *                        example/client/app.config.ts). The APNs topic is
 *                        derived as `${BUNDLE_ID}.voip`.
 *   APNS_KEY_ID          10-char key ID (paired with apns_key.p8)
 *   APNS_TEAM_ID         10-char team ID
 *   APNS_DEVICE_TOKEN    VoIP device token from getVoIPPushToken()
 *   FCM_TOKEN            FCM registration token from getVoIPPushToken()
 *
 * Required files in this directory:
 *   apns_key.p8          APNs auth key (.p8 PEM)
 *   fcm_key.json         FCM service-account JSON
 *
 * Zero npm deps — uses node:crypto, node:http2, fetch.
 */

import { join } from "node:path";

import { sendApns } from "./lib/apns";
import { apnsEnvSchema, fcmEnvSchema, parseEnv } from "./lib/env";
import { buildEvent } from "./lib/event";
import { sendFcm } from "./lib/fcm";

const APNS_KEY_PATH = join(import.meta.dir, "apns_key.p8");
const FCM_KEY_PATH = join(import.meta.dir, "fcm_key.json");

const args = process.argv.slice(2);
const flag = (name: string) => args.includes(name);
const arg = (name: string): string | undefined => {
  const i = args.indexOf(name);
  return i >= 0 ? args[i + 1] : undefined;
};

const event = buildEvent({
  eventId: arg("--eventId"),
  serverCallId: arg("--serverCallId"),
  hasVideo: flag("--video"),
  callerId: arg("--callerId"),
  displayName: arg("--display"),
  phoneNumber: arg("--phoneNumber"),
});

const forceIos = flag("--ios");
const forceAndroid = flag("--android");
const usePrd = flag("--production");

const want = (target: "ios" | "android") => {
  if (forceIos) return target === "ios";
  if (forceAndroid) return target === "android";
  return target === "ios"
    ? Boolean(process.env.APNS_DEVICE_TOKEN)
    : Boolean(process.env.FCM_TOKEN);
};

async function runApns(): Promise<void> {
  const env = parseEnv(apnsEnvSchema, "APNs");
  await sendApns(event, {
    keyPath: APNS_KEY_PATH,
    keyId: env.APNS_KEY_ID,
    teamId: env.APNS_TEAM_ID,
    topic: `${env.BUNDLE_ID}.voip`,
    deviceToken: env.APNS_DEVICE_TOKEN,
    production: usePrd,
  });
}

async function runFcm(): Promise<void> {
  const env = parseEnv(fcmEnvSchema, "FCM");
  await sendFcm(event, { keyPath: FCM_KEY_PATH, deviceToken: env.FCM_TOKEN });
}

async function main(): Promise<void> {
  console.log("Sending IncomingCallEvent:", JSON.stringify(event, null, 2));
  const jobs: Promise<void>[] = [];
  if (want("ios")) jobs.push(runApns());
  if (want("android")) jobs.push(runFcm());
  if (jobs.length === 0) {
    throw new Error(
      "No transport configured. Set APNS_DEVICE_TOKEN and/or FCM_TOKEN env vars.",
    );
  }
  const results = await Promise.allSettled(jobs);
  let failed = 0;
  for (const r of results) {
    if (r.status === "rejected") {
      console.error(
        "✗",
        r.reason instanceof Error ? r.reason.message : r.reason,
      );
      failed++;
    }
  }
  if (failed > 0) process.exit(1);
}

main().catch((e) => {
  console.error("✗", e instanceof Error ? e.message : e);
  process.exit(1);
});
