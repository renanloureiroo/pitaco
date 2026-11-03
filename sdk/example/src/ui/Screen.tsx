import type { ReactNode } from 'react';
import { ScrollView, StyleSheet, Text } from 'react-native';
import { usePalette } from './palette';

export function Screen({ children, testID }: { children: ReactNode; testID?: string }) {
  const palette = usePalette();
  return (
    <ScrollView
      testID={testID}
      style={{ backgroundColor: palette.background }}
      contentContainerStyle={styles.content}
      keyboardShouldPersistTaps="handled"
    >
      {children}
    </ScrollView>
  );
}

export function Paragraph({ children }: { children: ReactNode }) {
  const palette = usePalette();
  return <Text style={[styles.paragraph, { color: palette.text }]}>{children}</Text>;
}

export function Hint({ children }: { children: ReactNode }) {
  const palette = usePalette();
  return <Text style={[styles.hint, { color: palette.muted }]}>{children}</Text>;
}

const styles = StyleSheet.create({
  content: { padding: 16, gap: 12 },
  paragraph: { fontSize: 15, lineHeight: 22 },
  hint: { fontSize: 13, lineHeight: 19 },
});
