import type { AudioPort, AudioSession } from "expo-callkit-telecom";
import { StyleSheet, Text, View } from "react-native";

import { Card } from "./Card";

interface AudioSessionDetailsCardProps {
  audio: AudioSession;
}

/**
 * Full audio session snapshot, for checking how the session is configured
 * across a call (e.g. that a held call keeps its call category and the
 * pre-call category comes back after the last call ends).
 */
export function AudioSessionDetailsCard({
  audio,
}: AudioSessionDetailsCardProps) {
  const rows: [string, string | undefined][] = [
    ["Category", shortName(audio.category, "Category")],
    ["Mode", shortName(audio.mode, "Mode")],
    ["Options", audio.categoryOptions?.join(", ") || undefined],
    ["Active", yesNo(audio.isActive)],
    ["AV session active", yesNo(audio.avSessionIsActive)],
    ["RTC session active", yesNo(audio.rtcSessionIsActive)],
    ["RTC audio enabled", yesNo(audio.isAudioEnabled)],
    ["Manual audio", yesNo(audio.useManualAudio)],
    ["Other audio playing", yesNo(audio.isOtherAudioPlaying)],
    ["Microphone", audio.microphonePermission],
    ["Inputs", ports(audio.currentRoute.inputs)],
    ["Outputs", ports(audio.currentRoute.outputs)],
    ["Sample rate", `${audio.sampleRate} Hz`],
    ["IO buffer", `${(audio.ioBufferDuration * 1000).toFixed(1)} ms`],
    [
      "Channels (in / out)",
      `${audio.inputNumberOfChannels} / ${audio.outputNumberOfChannels}`,
    ],
  ];

  return (
    <Card title="Audio Session Details">
      {rows
        .filter((row): row is [string, string] => row[1] !== undefined)
        .map(([label, value]) => (
          <View key={label} style={styles.row}>
            <Text style={styles.label}>{label}</Text>
            <Text style={styles.value}>{value}</Text>
          </View>
        ))}
    </Card>
  );
}

/**
 * Strips the `AVAudioSessionCategory` / `AVAudioSessionMode` prefix from iOS
 * values (e.g. `AVAudioSessionCategoryPlayAndRecord` → `PlayAndRecord`).
 * Android values have no prefix and pass through unchanged.
 */
function shortName(value: string, kind: "Category" | "Mode"): string {
  return value.replace(`AVAudioSession${kind}`, "");
}

/** Formats an optional (iOS-only) flag, leaving it undefined so the row is hidden. */
function yesNo(value: boolean | undefined): string | undefined {
  return value === undefined ? undefined : value ? "yes" : "no";
}

function ports(list: AudioPort[]): string {
  return list.length > 0
    ? list.map((p) => `${p.portName} (${p.portType})`).join(", ")
    : "none";
}

const styles = StyleSheet.create({
  row: {
    flexDirection: "row",
    justifyContent: "space-between",
    gap: 12,
    paddingVertical: 3,
  },
  label: { fontSize: 12, color: "#5f6368" },
  value: {
    fontFamily: "Menlo",
    fontSize: 11,
    flexShrink: 1,
    textAlign: "right",
  },
});
