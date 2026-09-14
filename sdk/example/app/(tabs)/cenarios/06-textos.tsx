// Cenário 6 — Textos. Todos os rótulos da interface substituídos em inglês por um objeto `strings`.
import { useScenario } from '../../../src/pitaco/useScenario';
import { ENGLISH_CONFIG, ENGLISH_STRINGS } from '../../../src/scenarios/06-textos/english';
import { FlowModes } from '../../../src/scenarios/shared/FlowModes';
import { LocalPreview } from '../../../src/scenarios/shared/LocalPreview';
import { TriggerButton } from '../../../src/scenarios/shared/TriggerButton';
import { Hint, Paragraph, Screen } from '../../../src/ui/Screen';

export default function StringsScenario() {
  useScenario('06-textos', ENGLISH_CONFIG);

  return (
    <Screen testID="tela-06-textos">
      <Paragraph>Os rótulos da interface em inglês: Next, Back, Submit, progresso, obrigatória, agradecimento e acessibilidade.</Paragraph>
      <Hint>
        Os enunciados, as opções e o aviso de texto livre vêm da pesquisa publicada, então continuam em português: o
        objeto strings troca só o que é do SDK.
      </Hint>
      <FlowModes
        nn="06"
        real={<TriggerButton nn="06" />}
        preview={<LocalPreview scenarioId="06-textos" nn="06" strings={ENGLISH_STRINGS} />}
      />
    </Screen>
  );
}
