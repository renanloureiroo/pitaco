// Cenário 12 — a outra tela, onde a pesquisa adiada é liberada com `release()`. Mesmo id e mesmo
// prazo da tela anterior (parâmetro `prazo`), para o Provider manter o mesmo runtime.
import { usePitaco } from '@pitaco/react-native';
import { Stack, useLocalSearchParams } from 'expo-router';
import { useScenario } from '../../../src/pitaco/useScenario';
import { HoldCountdown } from '../../../src/scenarios/11-15-comum/HoldCountdown';
import { PlacementFeed } from '../../../src/scenarios/11-15-comum/PlacementFeed';
import { SurveyStatusLine } from '../../../src/scenarios/11-15-comum/SurveyStatusLine';
import { DEFER_CONFIGS, DEFER_SCENARIO_ID, deferTimeoutOf, parseDeadline } from '../../../src/scenarios/12-adiamento/config';
import { ActionButton } from '../../../src/ui/ActionButton';
import { Hint, Paragraph, Screen } from '../../../src/ui/Screen';

export default function ReleaseScreen() {
  const { prazo } = useLocalSearchParams<{ prazo?: string }>();
  const deadline = parseDeadline(prazo);
  useScenario(DEFER_SCENARIO_ID, DEFER_CONFIGS[deadline]);
  const { release } = usePitaco();

  return (
    <Screen testID="tela-12-adiamento-liberar">
      <Stack.Screen options={{ title: 'Liberar a pesquisa' }} />
      <Paragraph>Outra tela do app. Aqui a pesquisa adiada pode aparecer: toque em liberar antes de o prazo acabar.</Paragraph>
      <HoldCountdown scenarioId={DEFER_SCENARIO_ID} timeoutMs={deferTimeoutOf(deadline)} testID="cenario-12-liberar-prazo" />
      <ActionButton testID="cenario-12-liberar" label="Liberar (release)" onPress={release} />
      <SurveyStatusLine testID="cenario-12-liberar-status" />
      <PlacementFeed scenarioId={DEFER_SCENARIO_ID} testID="cenario-12-liberar-placement" />
      <Hint>Esperado: placement_released com heldMs e, logo depois, placement_available e o bottom sheet do SDK.</Hint>
    </Screen>
  );
}
