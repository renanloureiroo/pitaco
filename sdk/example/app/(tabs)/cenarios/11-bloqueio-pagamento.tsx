// Cenário 11 — a tela de pagamento. `<PitacoBlock />` bloqueia enquanto a tela estiver montada e
// desbloqueia ao desmontar (inclusive pela seta do cabeçalho ou pelo gesto de voltar).
import { PitacoBlock, usePitaco } from '@pitaco/react-native';
import { Stack, useRouter } from 'expo-router';
import { useState } from 'react';
import { seedSurvey } from '../../../src/pitaco/seed';
import { useScenario } from '../../../src/pitaco/useScenario';
import { BLOCK_CONFIG, BLOCK_HOLD_MS, BLOCK_REASON, BLOCK_SCENARIO_ID } from '../../../src/scenarios/11-bloqueio/config';
import { PaymentSummary } from '../../../src/scenarios/11-bloqueio/PaymentSummary';
import { HoldCountdown } from '../../../src/scenarios/11-15-comum/HoldCountdown';
import { PlacementFeed } from '../../../src/scenarios/11-15-comum/PlacementFeed';
import { SurveyStatusLine } from '../../../src/scenarios/11-15-comum/SurveyStatusLine';
import { usePolledDiagnostics } from '../../../src/scenarios/11-15-comum/usePolledDiagnostics';
import { ActionButton } from '../../../src/ui/ActionButton';
import { Hint, Paragraph, Screen } from '../../../src/ui/Screen';

export default function PaymentScreen() {
  useScenario(BLOCK_SCENARIO_ID, BLOCK_CONFIG);
  const { track } = usePitaco();
  const router = useRouter();
  const diagnostics = usePolledDiagnostics();
  const [paid, setPaid] = useState(false);

  return (
    <Screen testID="tela-11-bloqueio-pagamento">
      <Stack.Screen options={{ title: 'Pagamento' }} />
      <PitacoBlock reason={BLOCK_REASON} />
      <PaymentSummary />
      <ActionButton
        testID="cenario-11-pagar"
        label={paid ? 'Pago: disparar o evento de novo' : 'Pagar R$ 49,90'}
        disabled={seedSurvey === null}
        onPress={() => {
          setPaid(true);
          if (seedSurvey !== null) void track(seedSurvey.triggerEvent);
        }}
      />
      <Paragraph>
        Bloqueios ativos: {diagnostics === null ? '…' : diagnostics.blockedReasons.join(', ') || 'nenhum'} · pesquisa
        retida: {diagnostics?.held === true ? 'sim' : 'não'}
      </Paragraph>
      <SurveyStatusLine testID="cenario-11-pagamento-status" />
      <HoldCountdown scenarioId={BLOCK_SCENARIO_ID} timeoutMs={BLOCK_HOLD_MS} testID="cenario-11-pagamento-prazo" />
      <PlacementFeed scenarioId={BLOCK_SCENARIO_ID} testID="cenario-11-pagamento-placement" />
      <ActionButton testID="cenario-11-sair" variant="secondary" label="Concluir e sair do pagamento" onPress={() => router.back()} />
      <Hint>
        Esperado: placement_blocked ao abrir esta tela e placement_survey_held depois de pagar, sem nada na tela. Ao sair
        dentro do prazo, placement_available e a pesquisa abre na tela anterior.
      </Hint>
    </Screen>
  );
}
