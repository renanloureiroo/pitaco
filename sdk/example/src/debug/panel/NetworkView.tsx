// Requisições ao Pitaco e as respostas, capturadas pelo embrulho de `fetch` do exemplo.
import { useState } from 'react';
import { FlatList, Pressable, StyleSheet, Text, View } from 'react-native';
import { JsonBlock } from '../../ui/JsonBlock';
import { usePalette } from '../../ui/palette';
import { type NetworkEntry, useNetworkLog } from '../networkLog';

function pathOf(url: string): string {
  const index = url.indexOf('/collect/');
  return index >= 0 ? url.slice(index) : url;
}

function NetworkRow({ entry }: { entry: NetworkEntry }) {
  const palette = usePalette();
  const [open, setOpen] = useState(false);
  const failed = entry.error !== null || (entry.status !== null && entry.status >= 400);
  const status = entry.error !== null ? 'falhou' : entry.status === null ? '…' : String(entry.status);

  return (
    <Pressable
      testID={`requisicao-${entry.id}`}
      onPress={() => setOpen((value) => !value)}
      style={[styles.row, { borderColor: palette.border, backgroundColor: palette.surface }]}
    >
      <View style={styles.header}>
        <Text style={[styles.title, { color: palette.text }]} numberOfLines={1}>
          {entry.method} {pathOf(entry.url)}
        </Text>
        <Text style={[styles.title, { color: failed ? palette.danger : palette.accent }]}>{status}</Text>
      </View>
      <Text style={[styles.meta, { color: palette.muted }]}>
        {entry.at.slice(11, 23)}
        {entry.durationMs === null ? '' : ` · ${entry.durationMs} ms`}
      </Text>
      {open && (
        <View style={styles.details}>
          <JsonBlock value={{ url: entry.url, headers: entry.requestHeaders }} />
          {entry.requestBody !== null && <JsonBlock value={entry.requestBody} />}
          {entry.responseBody !== null && <JsonBlock value={entry.responseBody || '(resposta vazia)'} />}
          {entry.error !== null && <JsonBlock value={entry.error} />}
        </View>
      )}
    </Pressable>
  );
}

export function NetworkView() {
  const palette = usePalette();
  const log = useNetworkLog();
  return (
    <FlatList
      testID="lista-requisicoes"
      data={log}
      keyExtractor={(item) => item.id}
      renderItem={({ item }) => <NetworkRow entry={item} />}
      contentContainerStyle={styles.list}
      ListEmptyComponent={<Text style={{ color: palette.muted }}>Nenhuma requisição ainda.</Text>}
    />
  );
}

const styles = StyleSheet.create({
  list: { gap: 6 },
  row: { borderWidth: 1, borderRadius: 8, padding: 8, gap: 4 },
  header: { flexDirection: 'row', justifyContent: 'space-between', gap: 8 },
  title: { fontSize: 13, fontWeight: '600', flexShrink: 1 },
  meta: { fontSize: 11 },
  details: { gap: 6 },
});
