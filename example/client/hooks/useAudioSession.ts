import {
  addAudioRouteChangedListener,
  addAudioSessionActivatedListener,
  addAudioSessionDeactivatedListener,
  type AudioSession,
  getAudioSession,
} from "expo-callkit-telecom";
import { useEffect, useState } from "react";

/** Fallback refresh interval; see {@link useAudioSession}. */
const POLL_INTERVAL_MS = 1000;

/**
 * Tracks the current audio session state. Hydrates once from
 * {@link getAudioSession}, then refreshes whenever activation, deactivation,
 * or route-change events fire.
 *
 * Also polls once a second. Some changes have no event of their own: on iOS
 * the pre-call category is restored after the last call is removed, which can
 * land just after the deactivation event.
 */
export function useAudioSession(): AudioSession {
  const [audio, setAudio] = useState<AudioSession>(() => getAudioSession());

  useEffect(() => {
    const refresh = () => setAudio(getAudioSession());
    refresh();
    const subs = [
      addAudioSessionActivatedListener(refresh),
      addAudioSessionDeactivatedListener(refresh),
      addAudioRouteChangedListener(refresh),
    ];
    const poll = setInterval(refresh, POLL_INTERVAL_MS);
    return () => {
      subs.forEach((s) => s.remove());
      clearInterval(poll);
    };
  }, []);

  return audio;
}
