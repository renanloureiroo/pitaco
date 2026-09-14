// Ações do painel: instalação nova, app reaberto, copiar e limpar o log.
import { usePitaco } from '@pitaco/react-native';
import * as Clipboard from 'expo-clipboard';
import { useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { useExampleActions } from '../../pitaco/ExampleContext';
import { ActionButton } from '../../ui/ActionButton';
import { toJson } from '../../ui/JsonBlock';
import { eventLog } from '../eventLog';
import { networkLog } from '../networkLog';

export function DebugActions() {
  const { simulateAppReopen, diagnostics } = usePitaco();
  const { requestFreshInstall } = useExampleActions();
  const [copied, setCopied] = useState(false);

  const copyLog = async () => {
    const dump = { events: eventLog.getSnapshot(), requests: networkLog.getSnapshot(), diagnostics: diagnostics() };
    await Clipboard.setStringAsync(toJson(dump));
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  };

  return (
    <View style={styles.grid}>
      <ActionButton testID="debug-limpar-storage" variant="danger" label="Limpar storage e identidade" onPress={() => void requestFreshInstall()} />
      <ActionButton testID="debug-simular-reabertura" variant="secondary" label="Simular app reaberto" onPress={simulateAppReopen} />
      <View style={styles.row}>
        <View style={styles.cell}>
          <ActionButton testID="debug-copiar-log" variant="secondary" label={copied ? 'Copiado' : 'Copiar log'} onPress={() => void copyLog()} />
        </View>
        <View style={styles.cell}>
          <ActionButton
            testID="debug-limpar-log"
            variant="secondary"
            label="Limpar log"
            onPress={() => {
              eventLog.clear();
              networkLog.clear();
            }}
          />
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  grid: { gap: 6 },
  row: { flexDirection: 'row', gap: 6 },
  cell: { flex: 1 },
});
