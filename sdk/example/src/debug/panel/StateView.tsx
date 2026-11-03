// Identidade, fila local e portões do runtime, lidos de `usePitaco().diagnostics()` a cada segundo.
import { type RuntimeDiagnostics, usePitaco } from '@pitaco/react-native';
import { useEffect, useState } from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';
import { useExampleState } from '../../pitaco/ExampleContext';
import { HOME_SCENARIO_ID } from '../../pitaco/PitacoRoot';
import { JsonBlock } from '../../ui/JsonBlock';
import { usePalette } from '../../ui/palette';

interface DiagnosticsSnapshot {
  readonly value: RuntimeDiagnostics | null;
  readonly readAt: number;
}

function useDiagnostics(): DiagnosticsSnapshot {
  const { diagnostics } = usePitaco();
  const [snapshot, setSnapshot] = useState<DiagnosticsSnapshot>({ value: null, readAt: 0 });
  useEffect(() => {
    const read = () => setSnapshot({ value: diagnostics(), readAt: Date.now() });
    const first = setTimeout(read, 0);
    const timer = setInterval(read, 1000);
    return () => {
      clearTimeout(first);
      clearInterval(timer);
    };
  }, [diagnostics]);
  return snapshot;
}

function Field({ label, value, testID }: { label: string; value: string; testID?: string }) {
  const palette = usePalette();
  return (
    <View style={styles.field}>
      <Text style={[styles.label, { color: palette.muted }]}>{label}</Text>
      <Text testID={testID} selectable style={[styles.value, { color: palette.text }]}>
        {value}
      </Text>
    </View>
  );
}

export function StateView() {
  const palette = usePalette();
  const { profile, activeScenario } = useExampleState();
  const { value: diagnostics, readAt } = useDiagnostics();

  return (
    <ScrollView contentContainerStyle={styles.content}>
      <Field label="Perfil" value={`${profile.label} — ${profile.baseUrl || '(sem endereço)'}`} />
      <Field label="Cenário ativo" value={activeScenario?.scenarioId ?? HOME_SCENARIO_ID} testID="estado-cenario" />
      {diagnostics === null ? (
        <Text style={{ color: palette.danger }}>Runtime indisponível (configuração inválida ou ausente).</Text>
      ) : (
        <>
          <Field label="deviceId" value={diagnostics.deviceId ?? '(ainda não gerado)'} testID="estado-device-id" />
          <Field label="Pesquisa já exibida nesta sessão" value={diagnostics.sessionSurveyShown ? 'sim' : 'não'} testID="estado-sessao" />
          <Field label="Chave recusada" value={diagnostics.keyRejected ? 'sim' : 'não'} />
          <Field
            label="Rate limit (429) até"
            value={diagnostics.rateLimitedUntil > readAt ? new Date(diagnostics.rateLimitedUntil).toISOString() : '—'}
          />
          <Field label="Bloqueios ativos" value={diagnostics.blockedReasons.join(', ') || '—'} />
          <Field label="Adiamento / retida" value={`${diagnostics.deferred ? 'adiando' : '—'} / ${diagnostics.held ? 'retida' : '—'}`} />
          <Field label={`Fila local (${diagnostics.queue.length})`} value="" testID="estado-fila" />
          {diagnostics.queue.map((item) => (
            <JsonBlock key={item.id} value={item} />
          ))}
        </>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  content: { gap: 10, paddingBottom: 24 },
  field: { gap: 2 },
  label: { fontSize: 12, fontWeight: '600' },
  value: { fontSize: 13 },
});
