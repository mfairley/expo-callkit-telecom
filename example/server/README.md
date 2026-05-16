# expo-callkit-telecom example server

A standalone push-sender script that plays the role of a real backend. It builds an `IncomingCallEvent`, wraps it in the canonical `{ "incomingCall": ... }` envelope, and sends it to your device over the real APNs VoIP or FCM v1 transport — letting you validate the push flow end-to-end.

Zero npm deps. Uses only Node's built-in `crypto` (ES256 / RS256 JWT signing), `http2` (APNs HTTP/2), and `fetch` (FCM OAuth + send).

## Setup

```sh
cp .env.example .env
# edit .env with your credentials and device token
```

### iOS (APNs VoIP)

In Apple Developer:

1. Create an APNs auth key under *Certificates → Keys* — save the `.p8` to `./apns_key.p8` and note the 10-character key ID.
2. Find your team ID under *Membership*.

The APNs topic is derived from `BUNDLE_ID` at the top of `send-test-push.ts` (must match `example/client/app.config.ts`'s iOS bundle id).

In `.env`:

```
APNS_KEY_ID=XXXXXXXXXX
APNS_TEAM_ID=YYYYYYYYYY
APNS_DEVICE_TOKEN=<copy from the example client's console log>
```

### Android (FCM v1)

In the Firebase console:

1. *Project settings → Service accounts → Generate new private key* — save the JSON to `./fcm_key.json`.

In `.env`:

```
FCM_TOKEN=<copy from the example client's console log>
```

## Running

From `example/server/` (so bun loads `.env`):

```sh
bun send-test-push.ts                       # auto: sends to whichever transport has creds
bun send-test-push.ts --ios                 # force iOS only
bun send-test-push.ts --android             # force FCM only
bun send-test-push.ts --display Alice       # customise caller display name
bun send-test-push.ts --phoneNumber +14155551234   # E.164; the OS will match against contacts
bun send-test-push.ts --video               # video call
bun send-test-push.ts --eventId <uuid>      # custom event id (default: random)
bun send-test-push.ts --serverCallId <id>   # custom server-side call id
bun send-test-push.ts --production          # iOS prod APNs (default: sandbox)
```

## Validating

1. Run the client app on a real device.
2. Copy the *VoIP device token* logged to the client's console (`VoIP device token: ...`) into `.env` (`APNS_DEVICE_TOKEN` on iOS, `FCM_TOKEN` on Android).
3. Run `bun send-test-push.ts`. The script works the same regardless of whether the client is foregrounded, backgrounded, or force-quit — force-quit is just the most interesting case, since the native parser has to wake the app from cold.
4. The OS delivers the push, the native parser reads the payload, and CallKit / Telecom shows the call with the info from the push (caller name, server call id, etc.). The *Session* card on the client UI shows the parsed event verbatim.

