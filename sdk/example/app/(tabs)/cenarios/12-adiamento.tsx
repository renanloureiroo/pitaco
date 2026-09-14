// Cenário 12 — Adiamento. Aqui a pesquisa é adiada (`defer()`) e disparada; ela fica retida sem
// aparecer. Dois caminhos: liberar em outra tela (`release()`, em `12-adiamento-liberar.tsx`), ou
// esperar o prazo curto estourar e ver o descarte (`placement_expired`), sem abrir exibição.
import { usePitaco } from '@pitaco/react-native';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { seedSurvey } from '../../../src/pitaco/seed';
import { useScenario } from '../../../src/pitaco/useScenario';
import { HoldCountdown } from '../../../src/scenarios/11-15-comum/HoldCountdown';
import { PlacementFeed } from '../../../src/scenarios/11-15-comum/PlacementFeed';
import { ScenarioPrep } from '../../../src/scenarios/11-15-comum/ScenarioPrep';
import { SurveyStatusLine } from '../../../src/scenarios/11-15-comum/SurveyStatusLine';
import { usePolledDiagnostics } from '../../../src/scenarios/11-15-comum/usePolledDiagnostics';
import {
  DEFER_CONFIGS,
  DEFER_DEADLINE_OPTIONS,
  DEFER_SCENARIO_ID,
  type DeferDeadline,
  deferTimeoutOf,
  releaseRoute,
} from '../../../src/scenarios/12-adiamento/config';
import { ActionButton } from '../../../src/ui/ActionButton';
import { Hint, Paragraph, Screen } from '../../../src/ui/Screen';
import { Segmented } from '../../../src/ui/Segmented';

export default function DeferScenario() {
  const [deadline, setDeadline] = useState<DeferDeadline>('10');
  useScenario(DEFER_SCENARIO_ID, DEFER_CONFIGS[deadline]);
  const { defer, track } = usePitaco();
  const router = useRouter();
  const diagnostics = usePolledDiagnostics();

  return (
    <Screen testID="tela-12-adiamento">
      <Paragraph>
        &quot;Adiar e disparar&quot; chama defer() e depois track(): a pesquisa chega e fica retida. Libere em outra tela
        dentro do prazo, ou espere o prazo estourar e veja o descarte sem exibição.
      </Paragraph>
      <Segmented testIDPrefix="cenario-12-prazo" options={DEFER_DEADLINE_OPTIONS} value={deadline} onChange={setDeadline} />
      <ActionButton
        testID="cenario-12-adiar-disparar"
        label="Adiar e disparar"
        disabled={seedSurvey === null}
        onPress={() => {
          defer();
          if (seedSurvey !== null) void track(seedSurvey.triggerEvent);
        }}
      />
      <ActionButton
        testID="cenario-12-ir-liberar"
        variant="secondary"
        label="Ir para a outra tela (liberar lá)"
        onPress={() => router.push(releaseRoute(deadline))}
      />
      {seedSurvey === null && <Hint>Pesquisa do seed não encontrada: rode `npm run seed` com o backend no ar.</Hint>}
      <HoldCountdown scenarioId={DEFER_SCENARIO_ID} timeoutMs={deferTimeoutOf(deadline)} testID="cenario-12-prazo" />
      <Paragraph>
        Adiamento pedido: {diagnostics?.deferred === true ? 'sim' : 'não'} · pesquisa retida:{' '}
        {diagnostics?.held === true ? 'sim' : 'não'}
      </Paragraph>
      <SurveyStatusLine testID="cenario-12-status" />
      <PlacementFeed scenarioId={DEFER_SCENARIO_ID} testID="cenario-12-placement" />
      <ScenarioPrep prefix="cenario-12" />
      <Hint>
        O prazo é o deferTimeoutMs do Provider, trocado só neste cenário (o padrão é 5 min). Trocar o prazo recria o
        runtime e esquece a pesquisa retida. Depois do descarte, a aba Rede não mostra nenhum POST /collect/displays: a
        exibição nunca foi aberta. O pedido de adiamento continua valendo até um release().
      </Hint>
    </Screen>
  );
}
