// Cenário 8 — Slots. Cabeçalho e rodapé próprios na moldura da UI padrão; o resto (progresso,
// perguntas, botão de fechar, agradecimento) continua do SDK, e os eventos são os mesmos.
import { useScenario } from '../../../src/pitaco/useScenario';
import { BRAND_SLOTS, SLOTS_CONFIG } from '../../../src/scenarios/08-slots/slots';
import { FlowModes } from '../../../src/scenarios/shared/FlowModes';
import { LocalPreview } from '../../../src/scenarios/shared/LocalPreview';
import { TriggerButton } from '../../../src/scenarios/shared/TriggerButton';
import { Paragraph, Screen } from '../../../src/ui/Screen';

export default function SlotsScenario() {
  useScenario('08-slots', SLOTS_CONFIG);

  return (
    <Screen testID="tela-08-slots">
      <Paragraph>
        Cabeçalho com a marca do app e rodapé com botões próprios (Continuar, Voltar, Enviar respostas). Validação, navegação e
        eventos continuam com o core.
      </Paragraph>
      <FlowModes
        nn="08"
        real={<TriggerButton nn="08" />}
        preview={<LocalPreview scenarioId="08-slots" nn="08" slots={BRAND_SLOTS} />}
      />
    </Screen>
  );
}
