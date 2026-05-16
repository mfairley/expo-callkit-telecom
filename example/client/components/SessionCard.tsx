import type { CallSession, CallSessionStatus } from "expo-callkit-telecom";
import { StyleSheet, Text, View } from "react-native";

import { Card } from "./Card";

interface SessionCardProps {
  session: CallSession | null;
}

export function SessionCard({ session }: SessionCardProps) {
  const active = !!session;
  const hasVideo = session?.incomingCallEvent?.hasVideo ?? false;
  const muted = session?.isMuted ?? false;

  return (
    <Card title="Call Session">
      <View style={styles.row}>
        <Pill
          label={active ? "Active" : "Inactive"}
          color={active ? "green" : "gray"}
        />
        {active && session && (
          <>
            <Pill
              label={
                session.status[0].toUpperCase() + session.status.slice(1)
              }
              color={STATUS_COLOR[session.status]}
            />
            <Pill
              label={hasVideo ? "Video" : "Audio"}
              color={hasVideo ? "purple" : "blue"}
            />
            <Pill
              label={muted ? "Muted" : "Unmuted"}
              color={muted ? "orange" : "gray"}
            />
          </>
        )}
      </View>
    </Card>
  );
}

type PillColor = "green" | "gray" | "blue" | "purple" | "orange" | "yellow" | "red";

const STATUS_COLOR: Record<CallSessionStatus, PillColor> = {
  requesting: "yellow",
  connecting: "yellow",
  ringing: "yellow",
  connected: "green",
  ended: "red",
};

const PALETTE: Record<PillColor, { bg: string; fg: string; dot: string }> = {
  green: { bg: "#d1f5d3", fg: "#1a6b2c", dot: "#2ea043" },
  gray: { bg: "#f1f1f1", fg: "#5f6368", dot: "#9aa0a6" },
  blue: { bg: "#d8e8ff", fg: "#1a4b8c", dot: "#2670d9" },
  purple: { bg: "#ead8ff", fg: "#5a1d9c", dot: "#8b3edc" },
  orange: { bg: "#ffe1c4", fg: "#8a4a10", dot: "#e07a1f" },
  yellow: { bg: "#fff3c4", fg: "#7a5d10", dot: "#d9a91f" },
  red: { bg: "#fdd6d6", fg: "#8a1c1c", dot: "#d93636" },
};

function Pill({ label, color }: { label: string; color: PillColor }) {
  const c = PALETTE[color];
  return (
    <View style={[styles.pill, { backgroundColor: c.bg }]}>
      <View style={[styles.dot, { backgroundColor: c.dot }]} />
      <Text style={[styles.label, { color: c.fg }]}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: "row", flexWrap: "wrap", gap: 6 },
  pill: {
    flexDirection: "row",
    alignItems: "center",
    paddingVertical: 4,
    paddingHorizontal: 10,
    borderRadius: 999,
  },
  dot: { width: 8, height: 8, borderRadius: 4, marginRight: 6 },
  label: { fontSize: 12, fontWeight: "600" },
});
