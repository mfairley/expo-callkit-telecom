import {
  addAudioRouteChangedListener,
  addAudioSessionActivatedListener,
  addAudioSessionDeactivatedListener,
  addCallAnsweredListener,
  addCallEndedListener,
  addCallIntentReceivedListener,
  addCallSessionAddedListener,
  addCallSessionRemovedListener,
  addCallSessionUpdatedListener,
  addDTMFListener,
  addIncomingCallReportedListener,
  addOutgoingCallStartedListener,
  addReportedCallEndedListener,
  addSetHeldActionListener,
  addSetMutedActionListener,
  addVideoChangedListener,
  addVoIPPushTokenUpdatedListener,
} from "expo-callkit-telecom";
import { useEffect } from "react";

/**
 * Subscribes to every event the native module emits and forwards each one as
 * a `name` string to the supplied logger. Used to drive the example app's
 * event log.
 */
export function useAllEvents(log: (name: string) => void): void {
  useEffect(() => {
    const subs = [
      addCallSessionAddedListener(() => log("CallSessionAdded")),
      addCallSessionUpdatedListener(() => log("CallSessionUpdated")),
      addCallSessionRemovedListener(() => log("CallSessionRemoved")),
      addAudioSessionActivatedListener(() => log("AudioSessionActivated")),
      addAudioSessionDeactivatedListener(() => log("AudioSessionDeactivated")),
      addAudioRouteChangedListener(() => log("AudioRouteChanged")),
      addCallIntentReceivedListener(() => log("CallIntentReceived")),
      addOutgoingCallStartedListener(() => log("OutgoingCallStarted")),
      addIncomingCallReportedListener(() => log("IncomingCallReported")),
      addCallAnsweredListener(() => log("CallAnswered")),
      addCallEndedListener(() => log("CallEnded")),
      addReportedCallEndedListener(() => log("CallReportedEnded")),
      addSetMutedActionListener(() => log("SetMutedAction")),
      addVideoChangedListener(() => log("VideoChanged")),
      addSetHeldActionListener(() => log("SetHeldAction")),
      addDTMFListener(() => log("DTMF")),
      addVoIPPushTokenUpdatedListener(() => log("VoIPPushTokenUpdated")),
    ];
    return () => subs.forEach((s) => s.remove());
  }, [log]);
}
