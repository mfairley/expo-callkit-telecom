import { Pressable, StyleSheet, Text } from "react-native";

type Variant = "primary" | "destructive";

interface ActionButtonProps {
  title: string;
  onPress: () => void;
  disabled?: boolean;
  variant?: Variant;
}

const COLOR: Record<Variant, { bg: string; fg: string; pressed: string }> = {
  primary: { bg: "#2670d9", fg: "#fff", pressed: "#1a4b8c" },
  destructive: { bg: "#d93636", fg: "#fff", pressed: "#a01c1c" },
};

export function ActionButton({
  title,
  onPress,
  disabled = false,
  variant = "primary",
}: ActionButtonProps) {
  const c = COLOR[variant];
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      style={({ pressed }) => [
        styles.base,
        { backgroundColor: pressed && !disabled ? c.pressed : c.bg },
        disabled && styles.disabled,
      ]}
    >
      <Text style={[styles.label, { color: c.fg }, disabled && styles.disabledLabel]}>
        {title}
      </Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: {
    paddingVertical: 10,
    paddingHorizontal: 14,
    borderRadius: 8,
    alignItems: "center",
    justifyContent: "center",
  },
  disabled: {
    backgroundColor: "#d0d4da",
  },
  label: {
    fontSize: 15,
    fontWeight: "600",
  },
  disabledLabel: {
    color: "#7a808a",
  },
});
