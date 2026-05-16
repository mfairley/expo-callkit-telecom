import { useEffect, useState } from "react";

import { addVoIPPushTokenUpdatedListener, getVoIPPushToken } from "../Calls";
import type { PushTokenType, VoIPPushToken } from "../Calls.types";

/**
 * Hook that returns the current VoIP push token and subscribes to updates.
 *
 * Reads the initial token synchronously and re-renders whenever the native
 * `onVoIPPushTokenUpdated` event fires (e.g. when the OS provides a new token
 * or invalidates the existing one).
 *
 * On iOS this returns an APNs VoIP token; on Android it returns an FCM token.
 *
 * @returns The current token, or `null` if not yet available.
 */
export function useVoIPPushToken(): VoIPPushToken | null {
  const [token, setToken] = useState<VoIPPushToken | null>(() =>
    getVoIPPushToken(),
  );

  useEffect(() => {
    const subscription = addVoIPPushTokenUpdatedListener((event) => {
      setToken(
        event.token
          ? { token: event.token, type: event.type as PushTokenType }
          : null,
      );
    });
    return () => subscription.remove();
  }, []);

  return token;
}
