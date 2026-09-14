// Dispara o `track` do cenário 14 só depois de o Provider estar com a configuração da falha. Trocar a
// configuração recria o runtime no render seguinte; aqui, o contexto do exemplo (a configuração
// aplicada) e o `usePitaco()` (o runtime) são lidos no mesmo render, então quando a configuração
// aplicada é a da falha, o `track` já é o do runtime novo.
import { usePitaco } from '@pitaco/react-native';
import { useEffect, useRef } from 'react';
import { type ScenarioProviderConfig, useExampleState } from '../../pitaco/ExampleContext';
import { seedSurvey } from '../../pitaco/seed';

export function RunTrigger({ runId, config }: { runId: number | null; config: ScenarioProviderConfig }) {
  const { activeScenario } = useExampleState();
  const { track } = usePitaco();
  const fired = useRef<number | null>(null);
  const applied = activeScenario?.config === config;

  useEffect(() => {
    if (runId === null || !applied || fired.current === runId || seedSurvey === null) return;
    fired.current = runId;
    void track(seedSurvey.triggerEvent);
  }, [runId, applied, track]);

  return null;
}
