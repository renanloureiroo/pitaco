// O `<PitacoProvider>` único do exemplo, na raiz do app. A base vem do perfil de conexão; o cenário
// ativo (via `useScenario`) sobrepõe tema, textos, apresentação, renderizadores, slots e o que mais
// precisar. Todo evento vai para o painel de depuração, marcado com o cenário ativo.
import { PitacoProvider, type PitacoListenerEvent } from '@pitaco/react-native';
import { type ReactNode, useCallback } from 'react';
import { publishEvent } from '../debug/eventLog';
import { PITACO_API_KEY } from './config';
import { useExampleState } from './ExampleContext';

export const HOME_SCENARIO_ID = 'inicio';

export function PitacoRoot({ children }: { children: ReactNode }): ReactNode {
  const { profile, storage, activeScenario } = useExampleState();
  const scenarioId = activeScenario?.scenarioId ?? HOME_SCENARIO_ID;

  const onEvent = useCallback(
    (event: PitacoListenerEvent) => publishEvent(scenarioId, event, { source: 'provider' }),
    [scenarioId],
  );

  return (
    <PitacoProvider
      baseUrl={profile.baseUrl}
      apiKey={PITACO_API_KEY}
      storage={storage}
      onEvent={onEvent}
      debug={__DEV__}
      errorReporting
      {...activeScenario?.config}
    >
      {children}
    </PitacoProvider>
  );
}
