import { Pressable, StyleSheet, Text } from 'react-native';
import { usePalette } from './palette';

export interface ActionButtonProps {
  readonly label: string;
  readonly onPress: () => void;
  readonly testID?: string;
  readonly variant?: 'primary' | 'secondary' | 'danger';
  readonly disabled?: boolean;
}

export function ActionButton({ label, onPress, testID, variant = 'primary', disabled = false }: ActionButtonProps) {
  const palette = usePalette();
  const background = variant === 'primary' ? palette.accent : palette.surface;
  const color = variant === 'primary' ? palette.onAccent : variant === 'danger' ? palette.danger : palette.text;

  return (
    <Pressable
      testID={testID}
      accessibilityRole="button"
      accessibilityState={{ disabled }}
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [
        styles.button,
        { backgroundColor: background, borderColor: variant === 'primary' ? background : palette.border },
        (pressed || disabled) && styles.dimmed,
      ]}
    >
      <Text style={[styles.label, { color }]}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    minHeight: 44,
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderRadius: 10,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  label: { fontSize: 15, fontWeight: '600' },
  dimmed: { opacity: 0.6 },
});
