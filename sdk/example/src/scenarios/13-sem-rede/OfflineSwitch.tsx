// O interruptor "Simular sem rede": liga a falha de rede no embrulho de `fetch` do exemplo. Fica só
// em memória (`networkConditions.ts`), então o app reaberto volta sempre com rede.
import { StyleSheet, Switch, Text, View } from 'react-native';
import { setNetworkConditions, useNetworkConditions } from '../../debug/networkConditions';
import { usePalette } from '../../ui/palette';

export function OfflineSwitch() {
  const palette = usePalette();
  const { offline } = useNetworkConditions();

  return (
    <View style={[styles.row, { borderColor: palette.border, backgroundColor: palette.surface }]}>
      <View style={styles.texts}>
        <Text style={[styles.label, { color: palette.text }]}>Simular sem rede</Text>
        <Text testID="cenario-13-rede" style={[styles.detail, { color: offline ? palette.danger : palette.muted }]}>
          {offline ? 'Sem rede: as requisições do SDK falham como falha de rede.' : 'Com rede.'}
        </Text>
      </View>
      <Switch
        testID="cenario-13-sem-rede"
        accessibilityLabel="Simular sem rede"
        value={offline}
        onValueChange={(value) => setNetworkConditions({ offline: value })}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', gap: 12, borderWidth: 1, borderRadius: 10, padding: 12 },
  texts: { flex: 1, gap: 2 },
  label: { fontSize: 15, fontWeight: '600' },
  detail: { fontSize: 12 },
});
