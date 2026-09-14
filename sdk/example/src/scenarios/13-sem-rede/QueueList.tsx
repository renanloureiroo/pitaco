// A fila local do SDK (`diagnostics().queue`): o que foi gravado e ainda não foi entregue.
import type { QueueItem } from '@pitaco/react-native';
import { StyleSheet, Text, View } from 'react-native';
import { usePalette } from '../../ui/palette';

const KIND_LABELS: Record<QueueItem['kind'], string> = {
  open_display: 'abertura da exibição',
  events: 'eventos de interação',
  submission: 'resposta',
  suppression: 'supressão',
};

function describe(item: QueueItem): string {
  const display = 'displayId' in item ? ` · exibição ${item.displayId.slice(0, 8)}` : '';
  const events = item.kind === 'events' ? ` · ${item.events.length} evento(s)` : '';
  return `${KIND_LABELS[item.kind]}${display}${events} · tentativas: ${item.attempts}`;
}

export function QueueList({ queue }: { queue: readonly QueueItem[] | null }) {
  const palette = usePalette();

  return (
    <View style={[styles.box, { borderColor: palette.border, backgroundColor: palette.surface }]}>
      <Text testID="cenario-13-fila" style={[styles.title, { color: palette.text }]}>
        {queue === null ? 'Fila local: lendo…' : `Fila local: ${queue.length} item(ns) pendente(s)`}
      </Text>
      {queue?.map((item, index) => (
        <Text key={item.id} testID={`cenario-13-fila-${index + 1}`} style={[styles.row, { color: palette.text }]}>
          {describe(item)}
        </Text>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  box: { borderWidth: 1, borderRadius: 10, padding: 10, gap: 4 },
  title: { fontSize: 13, fontWeight: '700' },
  row: { fontSize: 12, lineHeight: 17 },
});
