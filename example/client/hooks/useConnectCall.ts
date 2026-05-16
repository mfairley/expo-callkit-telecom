import {
  addCallAnsweredListener,
  addCallEndedListener,
  addOutgoingCallStartedListener,
  failIncomingCallConnected,
  fulfillIncomingCallConnected,
  reportCallEnded,
  reportOutgoingCallConnected,
} from "expo-callkit-telecom";
import { useCallback, useEffect, useState } from "react";

interface Pending {
  outgoingId?: string;
  incomingId?: string;
  requestId?: string;
}

/**
 * Stand-in for the real media-library wiring in a media-free demo.
 *
 * In a real app the media library (LiveKit, WebRTC, …) would tell the OS
 * "audio is flowing now" via {@link reportOutgoingCallConnected} /
 * {@link fulfillIncomingCallConnected} once its room is connected. Here we
 * track the pending outgoing id / incoming requestId from the lib's events
 * and expose a `connect()` so the user can fire those acks manually from a
 * button. CallKit / Telecom then transitions to the connected state.
 */
export function useConnectCall(): {
  canConnect: boolean;
  connect: () => void;
  failConnect: () => void;
} {
  const [pending, setPending] = useState<Pending>({});

  useEffect(() => {
    const subs = [
      addOutgoingCallStartedListener(({ id }) =>
        setPending((p) => ({ ...p, outgoingId: id })),
      ),
      addCallAnsweredListener(({ id, requestId }) =>
        setPending((p) => ({ ...p, incomingId: id, requestId })),
      ),
      addCallEndedListener(() => setPending({})),
    ];
    return () => subs.forEach((s) => s.remove());
  }, []);

  const connect = useCallback(() => {
    if (pending.requestId) {
      fulfillIncomingCallConnected(pending.requestId).catch(() => {});
      setPending((p) => ({ ...p, incomingId: undefined, requestId: undefined }));
    } else if (pending.outgoingId) {
      reportOutgoingCallConnected(pending.outgoingId).catch(() => {});
      setPending((p) => ({ ...p, outgoingId: undefined }));
    }
  }, [pending]);

  const failConnect = useCallback(() => {
    if (pending.incomingId && pending.requestId) {
      failIncomingCallConnected(pending.incomingId, pending.requestId).catch(
        () => {},
      );
      setPending((p) => ({ ...p, incomingId: undefined, requestId: undefined }));
    } else if (pending.outgoingId) {
      reportCallEnded(pending.outgoingId, "failed").catch(() => {});
      setPending((p) => ({ ...p, outgoingId: undefined }));
    }
  }, [pending]);

  return {
    canConnect: !!(pending.requestId || pending.outgoingId),
    connect,
    failConnect,
  };
}
