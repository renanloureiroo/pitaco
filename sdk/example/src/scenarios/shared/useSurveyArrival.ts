// Acompanha a chegada de uma pesquisa pelo hook headless (`usePitacoSurvey().available`) para os
// cenários em que o app posiciona a pesquisa (`presentation="inline"`): abrir o sheet do gorhom,
// empilhar a rota da pesquisa, montar a UI headless ou o `<PitacoSurveyContent />` no meio da tela.
//
// `available` cai assim que o core conclui ou dispensa, antes do agradecimento e antes de o
// contêiner do app fechar. Por isso a exibição fica "ativa" do instante em que chega até o app
// chamar `finish()` (no `onFinish` do conteúdo ou quando o próprio contêiner termina de fechar).
import { usePitacoSurvey } from '@pitaco/react-native';
import { useCallback, useState } from 'react';

export function useSurveyArrival() {
  const survey = usePitacoSurvey();
  const [arrivedId, setArrivedId] = useState<string | null>(null);
  const [finishedId, setFinishedId] = useState<string | null>(null);

  // Estado derivado ajustado durante o render (padrão do React para "guardar algo do render
  // anterior"): uma exibição nova (`displayId` novo) reabre.
  if (survey.available && survey.displayId !== null && survey.displayId !== arrivedId) {
    setArrivedId(survey.displayId);
  }

  const finish = useCallback(() => setFinishedId(arrivedId), [arrivedId]);

  return { survey, arrivedId, active: arrivedId !== null && arrivedId !== finishedId, finish };
}
