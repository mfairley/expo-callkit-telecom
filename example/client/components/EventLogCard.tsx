import { Button, ScrollView, StyleSheet, Text, View } from "react-native";

import { Card } from "./Card";

interface EventLogCardProps {
  lines: string[];
  onClear: () => void;
}

export function EventLogCard({ lines, onClear }: EventLogCardProps) {
  return (
    <Card title="Event Log">
      {lines.length === 0 ? (
        <Text style={styles.dim}>(none yet)</Text>
      ) : (
        <ScrollView style={styles.scroll} nestedScrollEnabled>
          {lines.map((line, i) => (
            <Text key={i} style={styles.mono}>
              {line}
            </Text>
          ))}
        </ScrollView>
      )}
      <View style={styles.footer}>
        <Button title="Clear" onPress={onClear} disabled={lines.length === 0} />
      </View>
    </Card>
  );
}

const styles = StyleSheet.create({
  scroll: { maxHeight: 240 },
  mono: { fontFamily: "Menlo", fontSize: 11 },
  dim: { color: "#888" },
  footer: { marginTop: 8, alignItems: "flex-end" },
});
