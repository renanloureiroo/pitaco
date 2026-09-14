// Preparação para rodar o cenário de novo sem sair da tela. O servidor não reexibe uma pesquisa já
// respondida ou dispensada pelo mesmo respondente (precisa de identidade nova), e o SDK mostra uma
// pesquisa por sessão de app (precisa zerar o limite). São os mesmos botões do painel de depuração.
import { usePitaco } from '@pitaco/react-native';
import { useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { useExampleActions } from '../../pitaco/ExampleContext';
import { ActionButton } from '../../ui/ActionButton';

export function ScenarioPrep({ prefix }: { prefix: string }) {
  const { simulateAppReopen } = usePitaco();
  const { requestFreshInstall } = useExampleActions();
  const [busy, setBusy] = useState(false);

  return (
    <View style={styles.row}>
      <View style={styles.cell}>
        <ActionButton
          testID={`${prefix}-nova-identidade`}
          variant="secondary"
          label={busy ? 'Limpando…' : 'Nova identidade'}
          disabled={busy}
          onPress={() => {
            setBusy(true);
            void requestFreshInstall().finally(() => setBusy(false));
          }}
        />
      </View>
      <View style={styles.cell}>
        <ActionButton testID={`${prefix}-zerar-sessao`} variant="secondary" label="Zerar limite da sessão" onPress={simulateAppReopen} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', gap: 6 },
  cell: { flex: 1 },
});
