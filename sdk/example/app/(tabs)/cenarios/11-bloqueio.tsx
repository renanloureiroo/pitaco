// Cenário 11 — Bloqueio. A tela de pagamento (`11-bloqueio-pagamento.tsx`) monta
// `<PitacoBlock reason="pagamento" />`: lá o evento dispara e nada aparece, a pesquisa fica retida.
// Ao sair do pagamento o bloqueio acaba e a pesquisa retida abre aqui, se ainda estiver no prazo
// (decisão da fase 4). Passado o prazo, ela é descartada sem abrir exibição.
import { useRouter } from 'expo-router';
import { seedSurvey } from '../../../src/pitaco/seed';
import { useScenario } from '../../../src/pitaco/useScenario';
import { BLOCK_CONFIG, BLOCK_HOLD_MS, BLOCK_SCENARIO_ID, PAYMENT_ROUTE } from '../../../src/scenarios/11-bloqueio/config';
import { HoldCountdown } from '../../../src/scenarios/11-15-comum/HoldCountdown';
import { PlacementFeed } from '../../../src/scenarios/11-15-comum/PlacementFeed';
import { ScenarioPrep } from '../../../src/scenarios/11-15-comum/ScenarioPrep';
import { SurveyStatusLine } from '../../../src/scenarios/11-15-comum/SurveyStatusLine';
import { ActionButton } from '../../../src/ui/ActionButton';
import { Hint, Paragraph, Screen } from '../../../src/ui/Screen';

export default function BlockScenario() {
  useScenario(BLOCK_SCENARIO_ID, BLOCK_CONFIG);
  const router = useRouter();

  return (
    <Screen testID="tela-11-bloqueio">
      <Paragraph>
        A tela de pagamento tem um &lt;PitacoBlock reason=&quot;pagamento&quot; /&gt;. Lá, pagar dispara o evento do seed e
        nada aparece: a pesquisa fica retida. Ao sair do pagamento, ela abre aqui, se ainda estiver dentro do prazo.
      </Paragraph>
      <ActionButton
        testID="cenario-11-ir-pagamento"
        label="Ir para o pagamento"
        disabled={seedSurvey === null}
        onPress={() => router.push(PAYMENT_ROUTE)}
      />
      {seedSurvey === null && <Hint>Pesquisa do seed não encontrada: rode `npm run seed` com o backend no ar.</Hint>}
      <SurveyStatusLine testID="cenario-11-status" />
      <HoldCountdown scenarioId={BLOCK_SCENARIO_ID} timeoutMs={BLOCK_HOLD_MS} testID="cenario-11-prazo" />
      <PlacementFeed scenarioId={BLOCK_SCENARIO_ID} testID="cenario-11-placement" />
      <ScenarioPrep prefix="cenario-11" />
      <Hint>
        Prazo de retenção neste cenário: 60 s (deferTimeoutMs; o padrão é 5 min). Ficar mais que isso no pagamento vira
        placement_survey_discarded, sem exibição. Para repetir depois de responder ou dispensar, use &quot;Nova
        identidade&quot;.
      </Hint>
    </Screen>
  );
}
