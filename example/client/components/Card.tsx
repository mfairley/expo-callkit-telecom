import type { ReactNode } from "react";
import { StyleSheet, Text, View } from "react-native";

interface CardProps {
  title: string;
  children: ReactNode;
}

/** Section card shell used by the demo to group related content. */
export function Card({ title, children }: CardProps) {
  return (
    <View style={styles.card}>
      <Text style={styles.title}>{title}</Text>
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    backgroundColor: "#fff",
    borderRadius: 8,
    padding: 16,
    marginBottom: 16,
  },
  title: { fontSize: 16, fontWeight: "600", marginBottom: 8 },
});
