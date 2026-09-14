// Controlador e Provider do preview: a mesma máquina de estado e os mesmos eventos do schema em
// memória, sem transporte, sem sessão de app e sem envio. `index.ts` (o barril público de
// `@pitaco/react-native/preview`) reexporta tudo daqui, mais `<PitacoPreview />`
// (`PitacoPreview.tsx`) — que é quem monta a UI visual por cima deste Provider. Separado do
// barril para `PitacoPreview.tsx` poder importar `PitacoPreviewProvider` sem um ciclo de módulo
// (`index.ts` → `PitacoPreview.tsx` → `index.ts`).

import { createElement, type ReactNode, useEffect, useMemo } from 'react';
import type { InteractionEvent, Presentation } from '../catalog/events';
import { type Clock, createSystemClock } from '../core/clock';
import type { SurveyController, SurveyUserAction } from '../core/session/controller';
import { SurveySession } from '../core/session/session';
import type { SurveySnapshot } from '../core/session/snapshot';
import { normalizeSurvey, type Survey } from '../core/survey/schema';
import { systemTimers, type Timers } from '../core/timers';
import { uuidV4 } from '../core/uuid';
import { PitacoContext, type PitacoContextValue } from '../react/context';
import type { PartialPitacoStrings } from '../ui/strings/strings';
import type { PitacoThemeConfig } from '../ui/theme/tokens';
import type { PitacoRendererMap, PitacoSlotMap } from '../ui/types';

export interface PreviewOptions {
  // O schema no formato da elegibilidade (`DeliverableSurvey`). `surveyId` e `versionId` podem
  // faltar num rascunho: o preview preenche.
  readonly schema: unknown;
  readonly presentation?: Presentation;
  readonly triggerEvent?: string;
  readonly onEvent?: (event: InteractionEvent) => void;
  readonly clock?: Clock;
  readonly timers?: Timers;
  readonly uuid?: () => string;
}

export interface PreviewController extends SurveyController {
  // O schema normalizado, ou nulo quando ele não tem nenhuma pergunta renderizável.
  readonly survey: Survey | null;
  // Começa uma nova passada pela mesma pesquisa (depois de concluir ou dispensar).
  readonly restart: () => void;
  readonly dispose: () => void;
  readonly setEventListener: (listener: ((event: InteractionEvent) => void) | undefined) => void;
}

function withDraftDefaults(schema: unknown): unknown {
  if (typeof schema !== 'object' || schema === null || Array.isArray(schema)) return schema;
  return { surveyId: 'preview', versionId: 'preview', versionNumber: 0, ...schema };
}

export function createPreviewController(options: PreviewOptions): PreviewController {
  const clock = options.clock ?? createSystemClock();
  const timers = options.timers ?? systemTimers;
  const uuid = options.uuid ?? uuidV4;
  const parsed = normalizeSurvey(withDraftDefaults(options.schema));
  const survey = parsed.kind === 'survey' && parsed.survey.questions.length > 0 ? parsed.survey : null;
  const listeners = new Set<() => void>();
  let onEvent = options.onEvent;
  let session: SurveySession | null = null;
  let unsubscribe: (() => void) | null = null;

  const notify = () => {
    for (const listener of Array.from(listeners)) {
      try {
        listener();
      } catch {
        // Ouvinte quebrado não interrompe os outros.
      }
    }
  };

  const start = () => {
    unsubscribe?.();
    session?.dispose();
    session =
      survey === null
        ? null
        : new SurveySession({
            survey,
            displayId: uuid(),
            triggerEvent: options.triggerEvent ?? 'preview',
            clock,
            timers,
            onTransition: (events) => {
              for (const event of events) {
                try {
                  onEvent?.(event);
                } catch {
                  // O ouvinte é de quem usa o preview.
                }
              }
            },
          });
    unsubscribe = session === null ? null : session.subscribe(notify);
    notify();
  };

  start();

  return {
    survey,
    getSnapshot: (): SurveySnapshot | null => session?.getSnapshot() ?? null,
    subscribe: (listener) => {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
    present: (presentation) =>
      session?.dispatch({ type: 'present', presentation: presentation ?? options.presentation ?? 'bottom-sheet' }),
    dispatch: (action: SurveyUserAction) => session?.dispatch(action),
    restart: start,
    setEventListener: (listener) => {
      onEvent = listener;
    },
    dispose: () => {
      unsubscribe?.();
      session?.dispose();
      session = null;
      listeners.clear();
    },
  };
}

export interface PitacoPreviewProviderProps {
  readonly schema: unknown;
  readonly presentation?: Presentation;
  readonly triggerEvent?: string;
  readonly onEvent?: (event: InteractionEvent) => void;
  // Tema, textos, renderizadores e slots: a mesma configuração de customização do Provider de
  // verdade (`PitacoProviderProps`), só que sem transporte por baixo. `<PitacoPreview />` só
  // repassa estas props para cá.
  readonly theme?: PitacoThemeConfig;
  readonly strings?: PartialPitacoStrings;
  readonly renderers?: Partial<PitacoRendererMap>;
  readonly slots?: Partial<PitacoSlotMap>;
  // Chamado quando um renderizador ou slot substituído lança um erro (o mesmo relatório que o
  // Provider de verdade manda pelo transporte); o preview não tem transporte, então só repassa.
  readonly onRenderError?: (error: unknown, context?: Readonly<Record<string, string>>) => void;
  readonly children?: ReactNode;
}

export function PitacoPreviewProvider(props: PitacoPreviewProviderProps): ReactNode {
  const { schema, presentation, triggerEvent, onEvent, theme, strings, renderers, slots, onRenderError, children } = props;
  // `onEvent` já entra na criação (não só depois, no efeito abaixo): um filho que chama
  // `present()` no próprio efeito de montagem roda antes do efeito deste Provider (React comita
  // efeitos de baixo para cima), e um `onEvent` só ligado depois perderia justo o
  // `survey_presented` — o primeiro evento da exibição. O efeito abaixo continua existindo para
  // acompanhar troca de referência de `onEvent` depois da montagem, sem recriar o controlador.
  // Só o valor inicial de `onEvent` importa aqui; trocas depois da montagem são cobertas pelo
  // efeito abaixo (`controller.setEventListener`), sem recriar o controlador.
  const controller = useMemo(
    () =>
      createPreviewController({
        schema,
        onEvent,
        ...(presentation === undefined ? {} : { presentation }),
        ...(triggerEvent === undefined ? {} : { triggerEvent }),
      }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [schema, presentation, triggerEvent],
  );
  useEffect(() => {
    controller.setEventListener(onEvent);
  }, [controller, onEvent]);
  useEffect(() => () => controller.dispose(), [controller]);

  const value = useMemo<PitacoContextValue>(
    () => ({
      runtime: null,
      controller,
      reportRenderError: (error, context) => onRenderError?.(error, context),
      ui: {
        presentation: presentation ?? 'bottom-sheet',
        ...(theme === undefined ? {} : { theme }),
        ...(strings === undefined ? {} : { strings }),
        ...(renderers === undefined ? {} : { renderers }),
        ...(slots === undefined ? {} : { slots }),
      },
    }),
    [controller, presentation, theme, strings, renderers, slots, onRenderError],
  );
  return createElement(PitacoContext.Provider, { value }, children);
}
