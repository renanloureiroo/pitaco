// Cenário 2 — Com gorhom. `presentation="inline"`: o SDK não abre contêiner nenhum. O app escuta a
// pesquisa disponível (`usePitacoSurvey().available`), abre o `BottomSheetModal` dele e põe
// `<PitacoSurveyContent />` dentro (`src/scenarios/02-gorhom/`). A safe area vem do
// `useSafeAreaInsets()` e vai ao SDK (`insets`) e ao gorhom (`topInset`, respiro embaixo). O
// `BottomSheetModalProvider` está na raiz (`app/_layout.tsx`), para o sheet ficar acima das abas.
import { usePitacoTheme } from '@pitaco/react-native';
import { useMemo } from 'react';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import type { ScenarioProviderConfig } from '../../../src/pitaco/ExampleContext';
import { useScenario } from '../../../src/pitaco/useScenario';
import { GorhomSurveySheet } from '../../../src/scenarios/02-gorhom/GorhomSurveySheet';
import { TriggerButton } from '../../../src/scenarios/shared/TriggerButton';
import { useSurveyArrival } from '../../../src/scenarios/shared/useSurveyArrival';
import { Hint, Paragraph, Screen } from '../../../src/ui/Screen';

export default function GorhomScenario() {
  const insets = useSafeAreaInsets();
  const config = useMemo<ScenarioProviderConfig>(() => ({ presentation: 'inline', insets }), [insets]);
  useScenario('02-gorhom', config);
  const { active, finish, arrivedId } = useSurveyArrival();
  const theme = usePitacoTheme();

  return (
    <>
      <Screen testID="tela-02-gorhom">
        <Paragraph>
          A pesquisa abre dentro de um BottomSheetModal do @gorhom/bottom-sheet, com dois pontos de parada (60% e 92%),
          fundo escurecido e arrastar para fechar. O SDK só desenha o conteúdo.
        </Paragraph>
        <TriggerButton nn="02" />
        <Hint>
          Confira no painel o survey_dismissed de cada saída: arrastar para baixo (via swipe), tocar no fundo (backdrop), o
          voltar do Android (hardware_back) e o X do SDK (close_button). Concluir fecha o sheet sozinho. No texto livre, o
          sheet sobe para o ponto mais alto ao focar o campo.
        </Hint>
      </Screen>
      {/* Um BottomSheetModal novo por exibição: a mesma instância, apresentada de novo depois de uma
          dispensa e de "Limpar storage e identidade", não reabria (o present() do gorhom não fazia nada). */}
      <GorhomSurveySheet
        key={arrivedId ?? 'nenhuma'}
        open={active}
        onClosed={finish}
        backgroundColor={theme.tokens.colors.background}
        handleColor={theme.tokens.colors.border}
      />
    </>
  );
}
