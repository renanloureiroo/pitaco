// Depois de reabrir: a resposta gravada sem rede saiu da fila? Quantas vezes foi enviada, e o que o
// backend tem para aquela exibição?
import type { QueueItem } from '@pitaco/react-native';
import { useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { useNetworkLog } from '../../debug/networkLog';
import { ActionButton } from '../../ui/ActionButton';
import { usePalette } from '../../ui/palette';
import { checkBackend } from './backendCheck';
import type { OfflineRun } from './offlineRun';

function sumEvents(bodies: readonly string[]): { accepted: number; duplicated: number } {
  let accepted = 0;
  let duplicated = 0;
  for (const body of bodies) {
    try {
      const parsed = JSON.parse(body) as { accepted?: unknown; duplicated?: unknown };
      if (typeof parsed.accepted === 'number') accepted += parsed.accepted;
      if (typeof parsed.duplicated === 'number') duplicated += parsed.duplicated;
    } catch {
      // Corpo vazio ou ilegível: não entra na conta.
    }
  }
  return { accepted, duplicated };
}

export interface DeliveryCheckProps {
  readonly run: OfflineRun;
  readonly queue: readonly QueueItem[] | null;
  readonly onForget: () => void;
}

export function DeliveryCheck({ run, queue, onForget }: DeliveryCheckProps) {
  const palette = usePalette();
  const log = useNetworkLog();
  const [backend, setBackend] = useState<string | null>(null);

  const displayPath = `/collect/displays/${run.displayId}`;
  const submissions = log.filter((entry) => entry.url.endsWith(`${displayPath}/submission`));
  const accepted = submissions.filter((entry) => entry.status !== null && entry.status >= 200 && entry.status < 300).length;
  const failed = submissions.filter((entry) => entry.error !== null).length;
  const events = sumEvents(
    log.flatMap((entry) => (entry.url.endsWith(`${displayPath}/events`) && entry.responseBody !== null ? [entry.responseBody] : [])),
  );
  const inQueue = queue === null ? null : queue.some((item) => 'displayId' in item && item.displayId === run.displayId);

  const delivery =
    inQueue === null
      ? 'Lendo a fila…'
      : inQueue
        ? 'Pendente: ainda na fila local.'
        : accepted > 0
          ? `Entregue: saiu da fila (${accepted} envio aceito nesta abertura).`
          : 'Fora da fila, sem envio registrado nesta abertura (entregue antes?).';

  const consult = () => {
    setBackend('Consultando o backend…');
    checkBackend(run.displayId, run.surveyId)
      .then((result) =>
        setBackend(
          result.rows === 0
            ? 'Backend: nenhum registro para esta exibição ainda.'
            : `Backend: ${result.rows} registro(s) para esta exibição, desfecho ${result.outcome ?? '?'}.`,
        ),
      )
      .catch((error: unknown) => setBackend(`Não deu para consultar o backend: ${error instanceof Error ? error.message : String(error)}`));
  };

  return (
    <View style={[styles.box, { borderColor: palette.border, backgroundColor: palette.surface }]}>
      <Text style={[styles.title, { color: palette.text }]}>Resposta gravada sem rede (registro persistido)</Text>
      <Text style={[styles.row, { color: palette.muted }]}>Exibição:</Text>
      <Text testID="cenario-13-display-id" selectable style={[styles.row, { color: palette.text }]}>
        {run.displayId}
      </Text>
      <Text style={[styles.row, { color: palette.muted }]}>Na fila desde {run.queuedAt.slice(11, 19)} (UTC)</Text>
      <Text testID="cenario-13-entrega" style={[styles.status, { color: inQueue === false && accepted > 0 ? palette.accent : palette.text }]}>
        {delivery}
      </Text>
      <Text testID="cenario-13-envios" style={[styles.row, { color: palette.text }]}>
        Envios da resposta nesta abertura: {submissions.length} ({accepted} aceito, {failed} sem rede) · eventos aceitos:{' '}
        {events.accepted}, duplicados: {events.duplicated}
      </Text>
      <ActionButton testID="cenario-13-conferir-backend" variant="secondary" label="Conferir no backend" onPress={consult} />
      {backend !== null && (
        <Text testID="cenario-13-backend" style={[styles.row, { color: palette.text }]}>
          {backend}
        </Text>
      )}
      <ActionButton testID="cenario-13-esquecer" variant="secondary" label="Esquecer este registro" onPress={onForget} />
    </View>
  );
}

const styles = StyleSheet.create({
  box: { borderWidth: 1, borderRadius: 10, padding: 10, gap: 6 },
  title: { fontSize: 13, fontWeight: '700' },
  status: { fontSize: 15, fontWeight: '700' },
  row: { fontSize: 12, lineHeight: 17 },
});
