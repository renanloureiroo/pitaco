// Contador regressivo do prazo de retenção (`deferTimeoutMs`), a partir dos eventos `placement_`:
// começa quando a pesquisa é retida (`placement_survey_held` no bloqueio, `placement_deferred` no
// adiamento) e para quando ela é liberada ou descartada. O relógio do SDK também começa na
// retenção, não no `block()`/`defer()` (decisão da fase 4).
import type { PlacementEventType } from '@pitaco/react-native';
import { StyleSheet, Text } from 'react-native';
import { usePalette } from '../../ui/palette';
import { useNow } from './useNow';
import { type PlacementEntry, usePlacementEvents } from './usePlacementEvents';

const START: ReadonlySet<PlacementEventType> = new Set(['placement_survey_held', 'placement_deferred']);
const DISCARDED: ReadonlySet<PlacementEventType> = new Set(['placement_survey_discarded', 'placement_expired']);
const SHOWN: ReadonlySet<PlacementEventType> = new Set(['placement_released', 'placement_available']);

type HoldState =
  | { readonly kind: 'none' }
  | { readonly kind: 'holding'; readonly since: number }
  | { readonly kind: 'discarded'; readonly type: PlacementEventType }
  | { readonly kind: 'shown'; readonly type: PlacementEventType };

function holdState(entries: readonly PlacementEntry[]): HoldState {
  let startIndex = -1;
  entries.forEach((entry, index) => {
    if (START.has(entry.event.type)) startIndex = index;
  });
  const start = entries[startIndex];
  if (start === undefined) return { kind: 'none' };
  for (const entry of entries.slice(startIndex + 1)) {
    if (DISCARDED.has(entry.event.type)) return { kind: 'discarded', type: entry.event.type };
    if (SHOWN.has(entry.event.type)) return { kind: 'shown', type: entry.event.type };
  }
  return { kind: 'holding', since: Date.parse(start.at) };
}

function seconds(ms: number): string {
  return `${(Math.max(0, ms) / 1000).toFixed(1).replace('.', ',')} s`;
}

export function HoldCountdown({ scenarioId, timeoutMs, testID }: { scenarioId: string; timeoutMs: number; testID: string }) {
  const palette = usePalette();
  const now = useNow();
  const state = holdState(usePlacementEvents(scenarioId));

  let text: string;
  let color = palette.text;
  switch (state.kind) {
    case 'none':
      text = `Nenhuma pesquisa retida. Prazo configurado: ${seconds(timeoutMs)}.`;
      color = palette.muted;
      break;
    case 'holding':
      text = `Pesquisa retida. Prazo restante: ${now === null ? '…' : seconds(state.since + timeoutMs - now)}`;
      break;
    case 'discarded':
      text = `Prazo estourado: descartada sem abrir exibição (${state.type}).`;
      color = palette.danger;
      break;
    case 'shown':
      text = `Liberada dentro do prazo (${state.type}).`;
      color = palette.accent;
      break;
  }

  return (
    <Text testID={testID} style={[styles.text, { color }]}>
      {text}
    </Text>
  );
}

const styles = StyleSheet.create({
  text: { fontSize: 15, fontWeight: '600', fontVariant: ['tabular-nums'] },
});
