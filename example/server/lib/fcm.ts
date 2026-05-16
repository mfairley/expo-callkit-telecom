import { createSign } from "node:crypto";
import { readFile } from "node:fs/promises";

import type { IncomingCallEvent } from "./event";

export interface FcmConfig {
  keyPath: string;
  deviceToken: string;
}

interface ServiceAccount {
  client_email: string;
  private_key: string;
  project_id: string;
}

function b64url(input: string | Buffer): string {
  return Buffer.from(input).toString("base64url");
}

export async function sendFcm(
  event: IncomingCallEvent,
  config: FcmConfig,
): Promise<void> {
  const raw = await readFile(config.keyPath, "utf8");
  let account: ServiceAccount;
  try {
    account = JSON.parse(raw);
  } catch (e) {
    throw new Error(
      `${config.keyPath} is not valid JSON: ${e instanceof Error ? e.message : e}`,
    );
  }

  const header = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const now = Math.floor(Date.now() / 1000);
  const claims = b64url(
    JSON.stringify({
      iss: account.client_email,
      scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: "https://oauth2.googleapis.com/token",
      iat: now,
      exp: now + 3600,
    }),
  );
  const signer = createSign("SHA256");
  signer.update(`${header}.${claims}`);
  const sig = b64url(signer.sign(account.private_key));
  const jwt = `${header}.${claims}.${sig}`;

  const tokenRes = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });
  if (!tokenRes.ok) {
    throw new Error(
      `Google OAuth: ${tokenRes.status} ${await tokenRes.text()}`,
    );
  }
  const { access_token } = (await tokenRes.json()) as { access_token: string };

  const fcmRes = await fetch(
    `https://fcm.googleapis.com/v1/projects/${account.project_id}/messages:send`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${access_token}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        message: {
          token: config.deviceToken,
          data: {
            messageType: "incoming_call",
            incoming_call: JSON.stringify(event),
          },
          android: { priority: "HIGH" },
        },
      }),
    },
  );
  if (!fcmRes.ok) {
    throw new Error(`FCM: ${fcmRes.status} ${await fcmRes.text()}`);
  }
  console.log("✓ FCM sent");
}
