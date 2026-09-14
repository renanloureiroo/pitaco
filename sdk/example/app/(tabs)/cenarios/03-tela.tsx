// Cenário 3 — Como tela. `presentation="inline"`: quando a pesquisa fica disponível, o app empilha
// a rota `/cenarios/03-tela-pesquisa`, que desenha `<PitacoSurveyContent />` em tela cheia com o
// cabeçalho nativo da navegação.
import { useRouter } from 'expo-router';
import { useEffect, useRef } from 'react';
import { useScenario } from '../../../src/pitaco/useScenario';
import { INLINE_CONFIG } from '../../../src/scenarios/shared/configs';
import { TriggerButton } from '../../../src/scenarios/shared/TriggerButton';
import { useSurveyArrival } from '../../../src/scenarios/shared/useSurveyArrival';
import { Hint, Paragraph, Screen } from '../../../src/ui/Screen';

const SURVEY_ROUTE = '/cenarios/03-tela-pesquisa';

export default function ScreenScenario() {
  useScenario('03-tela', INLINE_CONFIG);
  const router = useRouter();
  const { arrivedId } = useSurveyArrival();
  const pushedRef = useRef<string | null>(null);

  useEffect(() => {
    if (arrivedId === null || arrivedId === pushedRef.current) return;
    pushedRef.current = arrivedId;
    router.push(SURVEY_ROUTE);
  }, [arrivedId, router]);

  return (
    <Screen testID="tela-03-tela">
      <Paragraph>A pesquisa abre como uma rota empilhada na navegação, com o cabeçalho nativo.</Paragraph>
      <TriggerButton nn="03" />
      <Hint>
        Concluir volta sozinho para esta tela. Sair pela seta do cabeçalho, pelo gesto de voltar do iOS ou pelo voltar do
        Android com a pesquisa aberta vira survey_dismissed com via navigation.
      </Hint>
    </Screen>
  );
}
