// Os eventos `placement_` que o `<PitacoProvider>` da raiz entregou enquanto um cenário estava
// ativo, lidos do mesmo log do painel de depuração (`src/debug/eventLog.ts`).
import { isPlacementEvent, type PlacementEvent } from '@pitaco/react-native';
import { useMemo } from 'react';
import { type DebugEvent, useEventLog } from '../../debug/eventLog';

export interface PlacementEntry {
  readonly id: string;
  readonly at: string;
  readonly event: PlacementEvent;
}

function toPlacementEntry(entry: DebugEvent): PlacementEntry | null {
  if (entry.source !== 'provider' || !isPlacementEvent(entry.event)) return null;
  return { id: entry.id, at: entry.at, event: entry.event };
}

export function usePlacementEvents(scenarioId: string): readonly PlacementEntry[] {
  const log = useEventLog();
  return useMemo(() => {
    const result: PlacementEntry[] = [];
    for (const entry of log) {
      if (entry.scenarioId !== scenarioId) continue;
      const placement = toPlacementEntry(entry);
      if (placement !== null) result.push(placement);
    }
    return result;
  }, [log, scenarioId]);
}
