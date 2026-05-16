import {
  addAudioRouteChangedListener,
  addAudioSessionActivatedListener,
  addAudioSessionDeactivatedListener,
  type AudioSession,
  getAudioSession,
} from "expo-callkit-telecom";
import { useEffect, useState } from "react";

/**
 * Tracks the current audio session state. Hydrates once from
 * {@link getAudioSession}, then refreshes whenever activation, deactivation,
 * or route-change events fire.
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
    return () => subs.forEach((s) => s.remove());
  }, []);

  return audio;
}
