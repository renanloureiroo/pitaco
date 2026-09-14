// `<PitacoPreview />`: a pesquisa desenhada com a UI padrão (ou substituída) sobre um schema em
// memória — sem transporte, sem sessão de app, sem envio e sem exibição aberta de verdade. É
// `PitacoPreviewProvider` + `<PitacoSurveyContent />`, inline por padrão: o painel (fase 7, via
// `react-native-web`) e qualquer reabertura local do rascunho encaixam este componente onde
// quiserem. Com `presentation="bottom-sheet"` ou `"modal"`, a pré-visualização abre no mesmo
// contêiner que o `<PitacoProvider>` usaria (`PitacoSurfaceHost`), para ver "como fica" de verdade
// — ainda sem transporte e sem exibição aberta.
//
// Eventos chegam só a `onEvent` — nunca ao catálogo de verdade (não há `PitacoRuntime` aqui).

import { type ReactNode } from 'react';
import type { InteractionEvent, Presentation } from '../catalog/events';
import type { PartialPitacoStrings } from '../ui/strings/strings';
import type { PitacoThemeConfig } from '../ui/theme/tokens';
import type { PitacoRendererMap, PitacoSlotMap } from '../ui/types';
import { PitacoSurveyContent, type PitacoSurveyFinishReason } from '../ui/content/PitacoSurveyContent';
import { PitacoErrorBoundary } from '../react/ErrorBoundary';
import { PitacoSurfaceHost } from '../react/SurfaceHost';
import { PitacoPreviewProvider } from './controller';

export interface PitacoPreviewProps {
  // O schema no formato da elegibilidade (`DeliverableSurvey`). Aceita um rascunho sem
  // `surveyId`/`versionId` — o preview preenche.
  readonly schema: unknown;
  // Padrão `'inline'`: só o conteúdo, sem contêiner. `'bottom-sheet'` e `'modal'` abrem o
  // contêiner do próprio SDK (o mesmo do `<PitacoProvider>`); `survey_presented` só sai quando a
  // animação de entrada termina, e `onFinish` só é chamado depois da animação de saída.
  readonly presentation?: Presentation;
  readonly triggerEvent?: string;
  readonly theme?: PitacoThemeConfig;
  readonly strings?: PartialPitacoStrings;
  readonly renderers?: Partial<PitacoRendererMap>;
  readonly slots?: Partial<PitacoSlotMap>;
  readonly onEvent?: (event: InteractionEvent) => void;
  readonly onFinish?: (reason: PitacoSurveyFinishReason) => void;
  // Duração da tela de agradecimento antes de `onFinish('completed')`. Padrão da própria
  // `<PitacoSurveyContent />` (2500 ms).
  readonly thankYouDurationMs?: number;
  // Muda de valor para reiniciar a pré-visualização do zero: uma pesquisa nova (`displayId`
  // novo), do começo, mesmo esquema. É o mesmo mecanismo que a prop `key` do React já dá —
  // documentado aqui como a forma pretendida porque `<PitacoPreview />` não expõe um `restart()`
  // imperativo (é um componente só declarativo). Passar um `key` próprio no lugar funciona
  // exatamente igual; `resetKey` só existe para quem prefere não gerenciar a própria `key`.
  readonly resetKey?: string | number;
}

export function PitacoPreview(props: PitacoPreviewProps): ReactNode {
  const {
    schema,
    presentation = 'inline',
    triggerEvent,
    theme,
    strings,
    renderers,
    slots,
    onEvent,
    onFinish,
    thankYouDurationMs,
    resetKey,
  } = props;

  return (
    <PitacoPreviewProvider
      key={resetKey}
      schema={schema}
      presentation={presentation}
      {...(triggerEvent === undefined ? {} : { triggerEvent })}
      {...(theme === undefined ? {} : { theme })}
      {...(strings === undefined ? {} : { strings })}
      {...(renderers === undefined ? {} : { renderers })}
      {...(slots === undefined ? {} : { slots })}
      {...(onEvent === undefined ? {} : { onEvent })}
    >
      {presentation === 'inline' ? (
        <PitacoSurveyContent
          {...(onFinish === undefined ? {} : { onFinish })}
          {...(thankYouDurationMs === undefined ? {} : { thankYouDurationMs })}
        />
      ) : (
        <PitacoErrorBoundary>
          <PitacoSurfaceHost
            {...(onFinish === undefined ? {} : { onFinish })}
            {...(thankYouDurationMs === undefined ? {} : { thankYouDurationMs })}
          />
        </PitacoErrorBoundary>
      )}
    </PitacoPreviewProvider>
  );
}
