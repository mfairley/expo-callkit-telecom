import { createSign } from "node:crypto";
import { readFile } from "node:fs/promises";
import { connect as http2Connect } from "node:http2";

import type { IncomingCallEvent } from "./event";

export interface ApnsConfig {
  keyPath: string;
  keyId: string;
  teamId: string;
  topic: string;
  deviceToken: string;
  production?: boolean;
}

function b64url(input: string | Buffer): string {
  return Buffer.from(input).toString("base64url");
}

export async function sendApns(
  event: IncomingCallEvent,
  config: ApnsConfig,
): Promise<void> {
  const key = await readFile(config.keyPath, "utf8");

  const header = b64url(
    JSON.stringify({ alg: "ES256", kid: config.keyId, typ: "JWT" }),
  );
  const claims = b64url(
    JSON.stringify({ iss: config.teamId, iat: Math.floor(Date.now() / 1000) }),
  );
  const signer = createSign("SHA256");
  signer.update(`${header}.${claims}`);
  const sig = b64url(signer.sign({ key, dsaEncoding: "ieee-p1363" }));
  const jwt = `${header}.${claims}.${sig}`;

  const host = config.production
    ? "api.push.apple.com"
    : "api.sandbox.push.apple.com";
  const body = JSON.stringify({ incoming_call: event });

  const client = http2Connect(`https://${host}`);
  try {
    await new Promise<void>((resolve, reject) => {
      const req = client.request({
        ":method": "POST",
        ":path": `/3/device/${config.deviceToken}`,
        authorization: `bearer ${jwt}`,
        "apns-topic": config.topic,
        "apns-push-type": "voip",
        "apns-priority": "10",
        "content-type": "application/json",
      });
      let resStatus = 0;
      let resBody = "";
      req.on("response", (h) => {
        resStatus = Number(h[":status"]);
      });
      req.on("data", (chunk) => (resBody += chunk));
      req.on("end", () => {
        if (resStatus === 200) {
          console.log(`✓ APNs sent (${host})`);
          resolve();
        } else {
          reject(new Error(`APNs ${resStatus}: ${resBody}`));
        }
      });
      req.on("error", reject);
      req.write(body);
      req.end();
    });
  } finally {
    client.close();
  }
}
