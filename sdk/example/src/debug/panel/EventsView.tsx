// Eventos ao vivo, em ordem de chegada, agrupáveis por exibição (`displayId`) ou por cenário e
// forma — lado a lado, as sequências das formas do cenário 4 devem ser iguais.
import { useMemo, useState } from 'react';
import { SectionList, StyleSheet, Text, View } from 'react-native';
import { usePalette } from '../../ui/palette';
import { Segmented } from '../../ui/Segmented';
import { type DebugEvent, useEventLog } from '../eventLog';
import { displayIdOf, EventRow } from './EventRow';

type GroupBy = 'tempo' | 'exibicao' | 'cenario';

const GROUP_OPTIONS = [
  { value: 'tempo', label: 'Ordem' },
  { value: 'exibicao', label: 'Exibição' },
  { value: 'cenario', label: 'Cenário/forma' },
] as const;

function groupKey(entry: DebugEvent, groupBy: GroupBy): string {
  if (groupBy === 'tempo') return 'Todos os eventos';
  if (groupBy === 'exibicao') return displayIdOf(entry) ?? 'Sem exibição (placement_)';
  return entry.form === null ? entry.scenarioId : `${entry.scenarioId} · ${entry.form}`;
}

export function EventsView() {
  const palette = usePalette();
  const log = useEventLog();
  const [groupBy, setGroupBy] = useState<GroupBy>('tempo');

  const sections = useMemo(() => {
    const groups = new Map<string, DebugEvent[]>();
    for (const entry of log) {
      const key = groupKey(entry, groupBy);
      const list = groups.get(key) ?? [];
      list.push(entry);
      groups.set(key, list);
    }
    return Array.from(groups, ([title, data]) => ({ title, data }));
  }, [log, groupBy]);

  return (
    <View style={styles.container}>
      <Segmented testIDPrefix="agrupar" options={GROUP_OPTIONS} value={groupBy} onChange={setGroupBy} />
      <SectionList
        testID="lista-eventos"
        sections={sections}
        keyExtractor={(item) => item.id}
        renderItem={({ item }) => <EventRow entry={item} />}
        renderSectionHeader={({ section }) => (
          <Text style={[styles.section, { color: palette.muted, backgroundColor: palette.background }]}>
            {section.title} ({section.data.length})
          </Text>
        )}
        ItemSeparatorComponent={Separator}
        ListEmptyComponent={<Text style={{ color: palette.muted }}>Nenhum evento ainda.</Text>}
        stickySectionHeadersEnabled
      />
    </View>
  );
}

function Separator() {
  return <View style={styles.separator} />;
}

const styles = StyleSheet.create({
  container: { flex: 1, gap: 8 },
  section: { fontSize: 12, fontWeight: '700', paddingVertical: 6 },
  separator: { height: 6 },
});
