import {
  type AudioSession,
  type CallSession,
  endCall,
  reportCallEnded,
  reportIncomingCall,
  reportVideo,
  setAudioSessionPortOverride,
  setHeld,
  setMuted,
  startOutgoingCall,
} from "expo-callkit-telecom";
import { randomUUID } from "expo-crypto";
import { Alert, StyleSheet, View } from "react-native";

import { ActionButton } from "./ActionButton";
import { Card } from "./Card";

interface ActionsCardProps {
  session: CallSession | null;
  audio: AudioSession;
  canConnect: boolean;
  onConnect: () => void;
  onFailConnect: () => void;
  onError: (message: string) => void;
}

export function ActionsCard({
  session,
  audio,
  canConnect,
  onConnect,
  onFailConnect,
  onError,
}: ActionsCardProps) {
  const simulateIncoming = (hasVideo: boolean) =>
    reportIncomingCall({
      eventId: randomUUID(),
      serverCallId: `local-${Date.now()}`,
      hasVideo,
      startedAt: new Date().toISOString(),
      caller: {
        id: "demo-caller",
        displayName: hasVideo ? "Demo Video Caller" : "Demo Audio Caller",
      },
    }).catch((e) => onError(`simulate error: ${e}`));

  const startOutgoing = (hasVideo: boolean) =>
    startOutgoingCall(
      {
        id: "demo-recipient",
        displayName: hasVideo ? "Demo Video Recipient" : "Demo Audio Recipient",
      },
      { hasVideo },
    ).catch((e) => onError(`outgoing error: ${e}`));

  const onSpeaker = audio.currentRoute.outputs.some(
    (o) => o.portType === "builtInSpeaker",
  );
  const hasVideo = session?.options.hasVideo ?? false;

  return (
    <Card title="Actions">
      <ActionButton
        title="Start outgoing call"
        disabled={!!session}
        onPress={() => promptCallKind("Start outgoing call", startOutgoing)}
      />
      <View style={styles.spacer} />
      <ActionButton
        title="Simulate incoming call"
        disabled={!!session}
        onPress={() =>
          promptCallKind("Simulate incoming call", simulateIncoming)
        }
      />
      <View style={styles.divider} />
      <ActionButton
        title="Connect Call"
        disabled={!canConnect}
        onPress={onConnect}
      />
      <View style={styles.spacer} />
      <ActionButton
        title="Fail Connection"
        variant="destructive"
        disabled={!canConnect}
        onPress={onFailConnect}
      />
      <View style={styles.divider} />
      <ActionButton
        title={session?.isMuted ? "Unmute" : "Mute"}
        disabled={!session}
        onPress={() => session && setMuted(session.id, !session.isMuted)}
      />
      <View style={styles.spacer} />
      <ActionButton
        title={session?.isOnHold ? "Resume" : "Hold"}
        disabled={!session}
        onPress={() => session && setHeld(session.id, !session.isOnHold)}
      />
      <View style={styles.spacer} />
      <ActionButton
        title={onSpeaker ? "Switch to Earpiece" : "Switch to Speaker"}
        disabled={!session || hasVideo}
        onPress={() => setAudioSessionPortOverride(!onSpeaker)}
      />
      <View style={styles.spacer} />
      <ActionButton
        title={hasVideo ? "Disable Video" : "Enable Video"}
        disabled={!session}
        onPress={() => session && reportVideo(session.id, !hasVideo)}
      />
      <View style={styles.divider} />
      <ActionButton
        title="End Call"
        variant="destructive"
        disabled={!session}
        onPress={() => session && endCall(session.id)}
      />
      <View style={styles.spacer} />
      <ActionButton
        title="Report Remote Ended"
        variant="destructive"
        disabled={!session}
        onPress={() =>
          session && reportCallEnded(session.id, "remoteEnded")
        }
      />
    </Card>
  );
}

function promptCallKind(title: string, choose: (hasVideo: boolean) => void) {
  Alert.alert(title, undefined, [
    { text: "Cancel", style: "cancel" },
    { text: "Audio", onPress: () => choose(false) },
    { text: "Video", onPress: () => choose(true) },
  ]);
}

const styles = StyleSheet.create({
  spacer: { height: 8 },
  divider: {
    height: 1,
    backgroundColor: "#eee",
    marginVertical: 12,
  },
});
