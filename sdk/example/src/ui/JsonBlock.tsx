import { Platform, StyleSheet, Text } from 'react-native';
import { usePalette } from './palette';

export function toJson(value: unknown): string {
  try {
    return JSON.stringify(value, null, 2) ?? 'undefined';
  } catch {
    return String(value);
  }
}

export function JsonBlock({ value }: { value: unknown }) {
  const palette = usePalette();
  return (
    <Text selectable style={[styles.code, { backgroundColor: palette.code, color: palette.text }]}>
      {typeof value === 'string' ? value : toJson(value)}
    </Text>
  );
}

const styles = StyleSheet.create({
  code: {
    fontFamily: Platform.select({ ios: 'Menlo', default: 'monospace' }),
    fontSize: 11,
    lineHeight: 15,
    padding: 8,
    borderRadius: 6,
  },
});
