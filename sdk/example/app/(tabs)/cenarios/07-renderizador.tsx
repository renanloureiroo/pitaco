// Cenário 7 — Renderizador substituído. Um NPS desenhado pelo app convivendo com os tipos padrão na
// mesma pesquisa, e um renderizador de avaliação que lança erro de propósito: a pergunta cai nas
// estrelas padrão e o SDK manda o relatório de erro, visível na aba de rede do painel.
import { useScenario } from '../../../src/pitaco/useScenario';
import { APP_RENDERERS, RENDERERS_CONFIG } from '../../../src/scenarios/07-renderizador/renderers';
import { FlowModes } from '../../../src/scenarios/shared/FlowModes';
import { LocalPreview } from '../../../src/scenarios/shared/LocalPreview';
import { TriggerButton } from '../../../src/scenarios/shared/TriggerButton';
import { Hint, Paragraph, Screen } from '../../../src/ui/Screen';

export default function RendererScenario() {
  useScenario('07-renderizador', RENDERERS_CONFIG);

  return (
    <Screen testID="tela-07-renderizador">
      <Paragraph>
        A pergunta 1 (NPS) usa o renderizador do app, com cores por faixa. A pergunta 3 (avaliação) usa um renderizador que
        lança erro: ela aparece com as estrelas padrão, e a pesquisa segue.
      </Paragraph>
      <Hint>
        Na aba Depuração, em Rede, confira o POST /collect/sdk-errors com kind render_error (toque no item para ver o corpo).
        O relatório sai uma vez por sessão para o mesmo erro, e o console mostra o aviso de desenvolvimento.
      </Hint>
      <FlowModes
        nn="07"
        real={<TriggerButton nn="07" />}
        preview={<LocalPreview scenarioId="07-renderizador" nn="07" renderers={APP_RENDERERS} />}
      />
      <Hint>No preview local não há transporte: o renderizador cai no padrão do mesmo jeito, mas nenhum relatório vai à rede.</Hint>
    </Screen>
  );
}
