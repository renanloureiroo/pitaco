// As requisições ao Pitaco desde um instante, lidas do mesmo log do painel de depuração (o embrulho
// de `fetch` de `src/debug/networkLog.ts`). Mostra a URL inteira: no cenário 15 é ela que prova que
// o SDK passou pelo prefixo do gateway.
import { StyleSheet, Text, View } from 'react-native';
import { type NetworkEntry, useNetworkLog } from '../../debug/networkLog';
import { usePalette } from '../../ui/palette';

export interface NetworkFeedProps {
  readonly testID: string;
  readonly title: string;
  // ISO 8601; só as requisições a partir dele. `null` mostra todas.
  readonly since: string | null;
  readonly limit?: number;
}

function statusOf(entry: NetworkEntry): string {
  if (entry.error !== null) return 'falhou';
  return entry.status === null ? '…' : String(entry.status);
}

export function NetworkFeed({ testID, title, since, limit = 8 }: NetworkFeedProps) {
  const palette = usePalette();
  const log = useNetworkLog();
  const entries = log.filter((entry) => since === null || entry.at >= since).slice(-limit);

  return (
    <View testID={testID} style={[styles.box, { borderColor: palette.border, backgroundColor: palette.surface }]}>
      <Text style={[styles.title, { color: palette.text }]}>{title}</Text>
      {entries.length === 0 && <Text style={[styles.row, { color: palette.muted }]}>Nenhuma requisição ainda.</Text>}
      {entries.map((entry) => {
        const failed = entry.error !== null || (entry.status !== null && entry.status >= 400);
        return (
          <View key={entry.id} testID={`${testID}-${entry.id}`} style={styles.item}>
            <Text style={[styles.row, { color: palette.text }]}>
              {entry.method} {entry.url}
            </Text>
            <Text style={[styles.row, { color: failed ? palette.danger : palette.accent }]}>
              {statusOf(entry)}
              {entry.durationMs === null ? '' : ` · ${entry.durationMs} ms`}
              {entry.error === null ? '' : ` · ${entry.error}`}
            </Text>
          </View>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  box: { borderWidth: 1, borderRadius: 10, padding: 10, gap: 6 },
  title: { fontSize: 13, fontWeight: '700' },
  item: { gap: 1 },
  row: { fontSize: 12, lineHeight: 17 },
});
