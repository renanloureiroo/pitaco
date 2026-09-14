// Lista ao vivo dos eventos `placement_` do cenário: o que o SDK decidiu antes de existir exibição
// (bloqueado, retida, adiada, liberada, descartada). Esses eventos só vão para o `onEvent` do app,
// nunca para o servidor.
import { StyleSheet, Text, View } from 'react-native';
import { usePalette } from '../../ui/palette';
import { usePlacementEvents } from './usePlacementEvents';

function describeData(data: object): string {
  const text = JSON.stringify(data);
  return text === '{}' ? '' : ` ${text}`;
}

export function PlacementFeed({ scenarioId, testID }: { scenarioId: string; testID: string }) {
  const palette = usePalette();
  const entries = usePlacementEvents(scenarioId);

  return (
    <View testID={testID} style={[styles.box, { borderColor: palette.border, backgroundColor: palette.surface }]}>
      <Text style={[styles.title, { color: palette.text }]}>Eventos placement_ recebidos ({entries.length})</Text>
      {entries.length === 0 ? (
        <Text style={[styles.row, { color: palette.muted }]}>Nenhum ainda.</Text>
      ) : (
        entries.map((entry, index) => (
          <Text key={entry.id} testID={`${testID}-${index + 1}`} style={[styles.row, { color: palette.text }]}>
            <Text style={{ color: palette.muted }}>{entry.at.slice(11, 19)} </Text>
            {entry.event.type}
            <Text style={{ color: palette.muted }}>{describeData(entry.event.data)}</Text>
          </Text>
        ))
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  box: { borderWidth: 1, borderRadius: 10, padding: 10, gap: 4 },
  title: { fontSize: 13, fontWeight: '700' },
  row: { fontSize: 12, lineHeight: 17 },
});
