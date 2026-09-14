// Onde o Provider desenha a apresentação padrão quando há pesquisa disponível: bottom sheet
// (padrão), modal em tela cheia, ou nada em `'inline'` — nesse modo o SDK não abre contêiner
// algum, e é o app quem posiciona `<PitacoSurveyContent />` onde quiser. Já nasce dentro do
// error boundary do Provider (fase 2), para uma falha aqui nunca derrubar o app.
//
// Construído só sobre a API pública (`usePitacoSurvey`, `usePitacoTheme`, `usePitacoStrings`) e
// sobre `<PitacoSurveyContent />` (fase 3a): a mesma coisa que um app faria para montar a própria
// apresentação em cima do SDK headless.

import { useContext, useState, type ReactNode } from 'react';
import type { DismissVia } from '../catalog/events';
import { PitacoSurveyContent, type PitacoSurveyFinishReason } from '../ui/content/PitacoSurveyContent';
import { BottomSheet } from '../ui/presentation/BottomSheet';
import { FullScreenModal } from '../ui/presentation/FullScreenModal';
import { resolveInsets } from '../ui/presentation/insets';
import { usePitacoStrings } from '../ui/strings/useStrings';
import { usePitacoTheme } from '../ui/theme/useTheme';
import { PitacoContext } from './context';
import { usePitacoSurvey } from './usePitacoSurvey';

export interface PitacoSurfaceHostProps {
  // Chamado quando o contêiner termina de fechar (depois da animação de saída), com o desfecho.
  // O `<PitacoProvider>` não passa; o `<PitacoPreview presentation="bottom-sheet" | "modal" />`
  // usa para avisar o app que a pré-visualização fechou.
  readonly onFinish?: (reason: PitacoSurveyFinishReason) => void;
  // Repassado a `<PitacoSurveyContent />` (padrão dela: 2500 ms).
  readonly thankYouDurationMs?: number;
}

export function PitacoSurfaceHost(props: PitacoSurfaceHostProps = {}): ReactNode {
  const { onFinish, thankYouDurationMs } = props;
  const context = useContext(PitacoContext);
  const survey = usePitacoSurvey();
  const theme = usePitacoTheme();
  const strings = usePitacoStrings();

  const presentation = context?.ui.presentation ?? 'bottom-sheet';
  const insets = resolveInsets(context?.ui.insets, context?.ui.getInsets);

  // Continua aberto do instante em que a pesquisa fica disponível até `<PitacoSurveyContent />`
  // avisar o desfecho (`onFinish`) — não até `survey.available` virar falso: `available` já cai
  // no instante em que o core conclui, antes do agradecimento ter qualquer chance de aparecer, e
  // no instante em que dispensa, antes da folha ter chance de animar a saída. É esta flag quem
  // decide se o contêiner deve estar montado; a apresentação em si decide sozinha, a partir dela,
  // quando a própria animação termina (`onOpened`/`onClosed`).
  // Inicializado a partir do valor já disponível na primeira renderização (o preview, e uma
  // consulta de elegibilidade que já resolveu antes do primeiro efeito, chegam assim): sem o
  // inicializador preguiçoso, a comparação abaixo nunca veria uma "mudança" nesse caso, porque
  // `trackedAvailable` já nasceria igual a `survey.available`.
  const [open, setOpen] = useState(() => survey.available);
  // Ajusta `open` a partir de `survey.available` durante o render (padrão recomendado pelo
  // React para "estado derivado de uma prop/valor externo que muda"), não dentro de um
  // `useEffect` — chamar `setState` direto no corpo de um efeito é o que o lint novo
  // (`react-hooks/set-state-in-effect`) rejeita.
  const [trackedAvailable, setTrackedAvailable] = useState(survey.available);
  // O desfecho relatado por `<PitacoSurveyContent />`, entregue a `onFinish` só quando a
  // apresentação termina de fechar. Sem relato (o contêiner fechou por outro motivo), vale dispensa.
  const [finishReason, setFinishReason] = useState<PitacoSurveyFinishReason>('dismissed');
  // Em `inline` não há contêiner, então nada aqui chega a fechar (`onFinish`/`onClosed` nunca
  // rodam). Sem zerar, uma pesquisa que chegou nesse modo deixaria `open` verdadeiro, e quando a
  // apresentação voltasse a `bottom-sheet` ou `modal` (o runtime é recriado, sem pesquisa) o
  // contêiner abriria vazio por cima do app, engolindo os toques. Zerar também `trackedAvailable`
  // faz uma pesquisa ainda disponível abrir normalmente na troca de `inline` para um contêiner.
  if (presentation === 'inline') {
    if (open) setOpen(false);
    if (trackedAvailable) setTrackedAvailable(false);
    return null;
  }
  if (survey.available !== trackedAvailable) {
    setTrackedAvailable(survey.available);
    if (survey.available) setOpen(true);
  }

  const handleOpened = () => survey.present();
  const handleClosed = () => {
    setOpen(false);
    onFinish?.(finishReason);
  };
  const handleFinish = (reason: PitacoSurveyFinishReason) => {
    setFinishReason(reason);
    setOpen(false);
  };
  // Vias que a própria apresentação detecta (arrastar, fundo, voltar do Android, a ação de
  // acessibilidade da alça): registra no core, que por sua vez leva `<PitacoSurveyContent />` a
  // notar o desfecho e chamar `onFinish` acima. O botão de fechar já dispensa por dentro do
  // conteúdo (`close_button`, fase 3a) e chega aqui do mesmo jeito, por `onFinish`.
  const handleDismiss = (via: DismissVia) => survey.dismiss(via);

  // `presentDeferred`: quem chama `present()` é este componente, só depois que a apresentação
  // relata que a animação de entrada terminou (`onOpened`) — nunca `<PitacoSurveyContent />`
  // sozinha, que abriria a exibição antes da folha estar de fato visível.
  const content = (
    <PitacoSurveyContent
      presentDeferred
      onFinish={handleFinish}
      {...(thankYouDurationMs === undefined ? {} : { thankYouDurationMs })}
    />
  );

  if (presentation === 'modal') {
    return (
      <FullScreenModal
        open={open}
        onOpened={handleOpened}
        onClosed={handleClosed}
        onDismiss={handleDismiss}
        insets={insets}
        theme={theme}
      >
        {content}
      </FullScreenModal>
    );
  }

  return (
    <BottomSheet
      open={open}
      onOpened={handleOpened}
      onClosed={handleClosed}
      onDismiss={handleDismiss}
      insets={insets}
      theme={theme}
      strings={strings}
    >
      {content}
    </BottomSheet>
  );
}
