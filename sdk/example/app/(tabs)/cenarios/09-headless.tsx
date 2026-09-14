// Cenário 9 — Headless. `presentation="inline"` e uma UI inteiramente própria sobre
// `usePitacoSurvey()` (`src/scenarios/09-headless/`), com as seis perguntas, voltar, pular,
// dispensar e concluir. O tracking continua completo: confira no painel.
import { useScenario } from '../../../src/pitaco/useScenario';
import { HeadlessSurvey } from '../../../src/scenarios/09-headless/HeadlessSurvey';
import { INLINE_CONFIG } from '../../../src/scenarios/shared/configs';
import { FlowModes } from '../../../src/scenarios/shared/FlowModes';
import { LocalPreview } from '../../../src/scenarios/shared/LocalPreview';
import { TriggerButton } from '../../../src/scenarios/shared/TriggerButton';
import { useSurveyArrival } from '../../../src/scenarios/shared/useSurveyArrival';
import { Hint, Paragraph, Screen } from '../../../src/ui/Screen';

export default function HeadlessScenario() {
  useScenario('09-headless', INLINE_CONFIG);
  const { arrivedId, active, finish } = useSurveyArrival();

  return (
    <Screen testID="tela-09-headless">
      <Paragraph>Nenhum componente visual do SDK: o cartão abaixo é do app, e cada toque chama uma ação do core.</Paragraph>
      <FlowModes
        nn="09"
        real={
          <>
            <TriggerButton nn="09" />
            {active && <HeadlessSurvey key={arrivedId} onDone={finish} />}
          </>
        }
        preview={
          <LocalPreview scenarioId="09-headless" nn="09">
            {(onFinish) => <HeadlessSurvey onDone={onFinish} />}
          </LocalPreview>
        }
      />
      <Hint>A sequência de eventos no painel é a mesma da UI padrão para o mesmo caminho de respostas.</Hint>
    </Screen>
  );
}
