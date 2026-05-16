import {
  addCallSessionAddedListener,
  addCallSessionRemovedListener,
  addCallSessionUpdatedListener,
  type CallSession,
  getActiveCallSession,
} from "expo-callkit-telecom";
import { useEffect, useState } from "react";

/**
 * Tracks the currently active call session. Hydrates once from
 * {@link getActiveCallSession} (in case the JS layer started after a call
 * was already in progress), then keeps the state in sync via the lib's
 * three session lifecycle listeners.
 */
export function useCallSession(): CallSession | null {
  const [session, setSession] = useState<CallSession | null>(null);

  useEffect(() => {
    getActiveCallSession().then(setSession);
    const subs = [
      addCallSessionAddedListener((e) => setSession(e.session)),
      addCallSessionUpdatedListener((e) => setSession(e.session)),
      addCallSessionRemovedListener(() => setSession(null)),
    ];
    return () => subs.forEach((s) => s.remove());
  }, []);

  return session;
}
