import type { AudioSession } from "expo-callkit-telecom";
import { StyleSheet, Text, View } from "react-native";

import { Card } from "./Card";

interface AudioSessionCardProps {
  audio: AudioSession;
}

export function AudioSessionCard({ audio }: AudioSessionCardProps) {
  const active = audio.isActive;
  const onSpeaker = audio.currentRoute.outputs.some(
    (o) => o.portType === "builtInSpeaker",
  );

  return (
    <Card title="Audio Session">
      <View style={styles.row}>
        <Pill
          label={active ? "Active" : "Inactive"}
          color={active ? "green" : "gray"}
        />
        <Pill
          label={onSpeaker ? "Speaker" : "Earpiece"}
          color={onSpeaker ? "blue" : "gray"}
        />
      </View>
    </Card>
  );
}

type PillColor = "green" | "gray" | "blue";

const PALETTE: Record<PillColor, { bg: string; fg: string; dot: string }> = {
  green: { bg: "#d1f5d3", fg: "#1a6b2c", dot: "#2ea043" },
  gray: { bg: "#f1f1f1", fg: "#5f6368", dot: "#9aa0a6" },
  blue: { bg: "#d8e8ff", fg: "#1a4b8c", dot: "#2670d9" },
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
