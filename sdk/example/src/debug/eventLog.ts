// Log de eventos do painel de depuração: tudo o que chega em `onEvent`, do `<PitacoProvider>` da
// raiz ou de um `<PitacoPreview>`/`PitacoPreviewProvider` de algum cenário.
//
// Contrato para 5c/5d: eventos do Provider da raiz já entram sozinhos, marcados com o cenário
// ativo (o de `useScenario`). Quem usa o preview publica à mão, dizendo a forma:
//
//   const publish = usePublishEvent('04-comparar');
//   <PitacoPreview schema={seedSurvey.schema} onEvent={(event) => publish(event, { form: 'gorhom' })} />
import type { PitacoListenerEvent } from '@pitaco/react-native';
import { useCallback } from 'react';
import { createLogStore, useLogEntries } from './logStore';

export type EventSource = 'provider' | 'preview';

export interface DebugEvent {
  readonly id: string;
  readonly at: string;
  readonly scenarioId: string;
  // Rótulo livre da forma de apresentação (`'sheet'`, `'gorhom'`, `'tela'`...), para comparar
  // sequências lado a lado no cenário 4. `null` quando o cenário não distingue formas.
  readonly form: string | null;
  readonly source: EventSource;
  readonly event: PitacoListenerEvent;
}

export interface PublishOptions {
  readonly form?: string | null;
  readonly source?: EventSource;
}

export const eventLog = createLogStore<DebugEvent>(1000);

let counter = 0;

export function publishEvent(scenarioId: string, event: PitacoListenerEvent, options: PublishOptions = {}): void {
  counter += 1;
  eventLog.push({
    id: `e${counter}`,
    at: new Date().toISOString(),
    scenarioId,
    form: options.form ?? null,
    source: options.source ?? 'preview',
    event,
  });
}

export function usePublishEvent(scenarioId: string) {
  return useCallback(
    (event: PitacoListenerEvent, options?: PublishOptions) => publishEvent(scenarioId, event, options),
    [scenarioId],
  );
}

export function useEventLog(): readonly DebugEvent[] {
  return useLogEntries(eventLog);
}
