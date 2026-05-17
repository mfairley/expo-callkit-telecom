---
description: VoIP push payload shape for expo-callkit-telecom — APNs VoIP (PushKit) on iOS and FCM data messages on Android. Parsed natively so incoming calls report from a terminated state.
---

# VoIP push payload

When the OS delivers a VoIP push ([PushKit](https://developer.apple.com/documentation/pushkit) on iOS, an [FCM](https://firebase.google.com/docs/cloud-messaging) data message on Android), the module parses the payload natively — before JS is running — and reports the call to the OS.

The event itself is the same shape on both transports. All keys are camelCase:

```jsonc
// IncomingCallEvent (the "inner" event)
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",   // required (UUID), for dedup
  "serverCallId": "9e7f...",                           // required — your backend's call id
                                                       //   (distinct from CallSession.id,
                                                       //    which is the OS-assigned UUID)
  "hasVideo": false,
  "startedAt": "2026-01-15T19:42:11.000Z",             // RFC 3339, optional
  "caller": {
    "id": "<caller id>",                               // required — opaque, stable
    "displayName": "Jane Smith",
    "avatarUrl": "https://...",
    "phoneNumber": "+14155551234",                     // optional; must be E.164 if present
    "email": "jane@example.com"
  },
  "metadata": {                                        // optional, opaque pass-through
    "chatId": "abc-123",
    "tenantId": "acme-co"
  }
}
```

Any keys you put under `metadata` are forwarded verbatim from the push payload all the way through to your JS event handler. The lib treats them as opaque — you cast at the read site:

```ts
Calls.addCallAnsweredListener(({ id }) => {
  const session = /* lookup */;
  const chatId = session?.incomingCallEvent?.metadata?.chatId as string | undefined;
});
```

Both transports wrap the event under an `incomingCall` key, just at different layers — APNs in the push payload dictionary, FCM in the data block.

## iOS — APNs VoIP push

Send a [VoIP push through APNs](https://developer.apple.com/documentation/usernotifications/sending-voip-pushes) (`apns-push-type: voip`) whose dictionary payload nests the event under `incomingCall`:

```jsonc
{
  "incomingCall": { /* IncomingCallEvent — see above */ }
}
```

## Android — FCM data message

FCM data values must be strings, so JSON-encode the inner event and put it under `incomingCall`:

```jsonc
{
  "data": {
    "messageType": "incomingCall",
    "incomingCall": "{\"eventId\":\"...\",\"serverCallId\":\"...\", ... }"
  }
}
```

Non-`incomingCall` data messages are forwarded to [`expo-notifications`](https://docs.expo.dev/versions/latest/sdk/notifications/)'s service for normal handling.
