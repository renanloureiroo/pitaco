import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { JsonBlock } from '../../ui/JsonBlock';
import { usePalette } from '../../ui/palette';
import type { DebugEvent } from '../eventLog';

export function displayIdOf(entry: DebugEvent): string | null {
  const event = entry.event as { displayId?: unknown };
  return typeof event.displayId === 'string' ? event.displayId : null;
}

export function EventRow({ entry }: { entry: DebugEvent }) {
  const palette = usePalette();
  const [open, setOpen] = useState(false);
  const event = entry.event as { type: string; seq?: number; questionKey?: string; data?: { via?: unknown } };
  // A via da dispensa fica à vista na linha: é o que se compara entre as formas (e o que o Maestro confere).
  const via = typeof event.data?.via === 'string' ? event.data.via : null;
  const displayId = displayIdOf(entry);
  const origin = entry.form === null ? entry.scenarioId : `${entry.scenarioId} · ${entry.form}`;

  return (
    <Pressable
      testID={`evento-${entry.id}`}
      onPress={() => setOpen((value) => !value)}
      style={[styles.row, { borderColor: palette.border, backgroundColor: palette.surface }]}
    >
      <View style={styles.header}>
        <Text style={[styles.type, { color: palette.text }]}>
          {event.seq === undefined ? '' : `#${event.seq} `}
          {event.type}
          {via === null ? '' : ` · via ${via}`}
        </Text>
        <Text style={[styles.meta, { color: palette.muted }]}>{entry.at.slice(11, 23)}</Text>
      </View>
      <Text style={[styles.meta, { color: palette.muted }]}>
        {origin} · {entry.source}
        {displayId === null ? '' : ` · ${displayId.slice(0, 8)}`}
        {event.questionKey === undefined ? '' : ` · pergunta ${event.questionKey.slice(0, 8)}`}
      </Text>
      {open && <JsonBlock value={entry.event} />}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  row: { borderWidth: 1, borderRadius: 8, padding: 8, gap: 4 },
  header: { flexDirection: 'row', justifyContent: 'space-between', gap: 8 },
  type: { fontSize: 13, fontWeight: '600', flexShrink: 1 },
  meta: { fontSize: 11 },
});
