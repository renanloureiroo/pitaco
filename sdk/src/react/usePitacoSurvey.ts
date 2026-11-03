import { useContext, useEffect, useMemo, useSyncExternalStore } from 'react';
import type { DismissVia, Presentation } from '../catalog/events';
import type { SessionStatus, ValidationError } from '../core/machine/machine';
import type { SurveyController, SurveyUserAction } from '../core/session/controller';
import type { SurveyProgress, SurveySnapshot, SurveySummary } from '../core/session/snapshot';
import type { AnswerValue } from '../core/survey/answers';
import type { SurveyQuestion } from '../core/survey/schema';
import { PitacoContext } from './context';
import { warnOutsideProvider } from './usePitaco';

export interface PitacoSurveyState {
  // Há pesquisa pronta para exibir ou em exibição.
  readonly available: boolean;
  readonly status: SessionStatus | 'idle';
  readonly displayId: string | null;
  readonly survey: SurveySummary | null;
  readonly questions: readonly SurveyQuestion[];
  readonly question: SurveyQuestion | null;
  readonly answers: Readonly<Record<string, AnswerValue>>;
  readonly value: AnswerValue | undefined;
  readonly progress: SurveyProgress;
  readonly error: ValidationError | null;
  readonly focusedQuestionKey: string | null;
  readonly canGoBack: boolean;
  readonly canGoNext: boolean;
  readonly isLast: boolean;
}

// As ações do core. Qualquer UI que as use produz o mesmo fluxo de eventos.
export interface PitacoSurveyActions {
  // O contêiner ficou visível de fato: abre a exibição e emite `survey_presented`.
  present(presentation?: Presentation): void;
  select(value: string | number, questionKey?: string): void;
  deselect(value?: string | number, questionKey?: string): void;
  setText(text: string, questionKey?: string): void;
  focusText(questionKey?: string): void;
  blurText(questionKey?: string): void;
  next(): void;
  back(): void;
  // Como a pessoa fechou. O contêiner do app informa a via (o `onDismiss` do gorhom, por exemplo).
  dismiss(via?: DismissVia): void;
  complete(): void;
}

export type UsePitacoSurveyResult = PitacoSurveyState & PitacoSurveyActions;

const EMPTY_PROGRESS: SurveyProgress = { position: 0, total: 0 };
const EMPTY_ANSWERS: Readonly<Record<string, AnswerValue>> = {};

const IDLE: PitacoSurveyState = {
  available: false,
  status: 'idle',
  displayId: null,
  survey: null,
  questions: [],
  question: null,
  answers: EMPTY_ANSWERS,
  value: undefined,
  progress: EMPTY_PROGRESS,
  error: null,
  focusedQuestionKey: null,
  canGoBack: false,
  canGoNext: false,
  isLast: false,
};

function toState(snapshot: SurveySnapshot | null): PitacoSurveyState {
  if (snapshot === null) return IDLE;
  return {
    available: snapshot.status === 'ready' || snapshot.status === 'presented',
    status: snapshot.status,
    displayId: snapshot.displayId,
    survey: snapshot.survey,
    questions: snapshot.questions,
    question: snapshot.question,
    answers: snapshot.answers,
    value: snapshot.value,
    progress: snapshot.progress,
    error: snapshot.validationError,
    focusedQuestionKey: snapshot.focusedQuestionKey,
    canGoBack: snapshot.canGoBack,
    canGoNext: snapshot.canGoNext,
    isLast: snapshot.isLast,
  };
}

const noopSubscribe = () => () => undefined;
const nullSnapshot = () => null;

export function createSurveyActions(controller: SurveyController | null): PitacoSurveyActions {
  const dispatch = (action: SurveyUserAction) => {
    try {
      controller?.dispatch(action);
    } catch {
      // O controlador já é seguro; isto é só a última rede.
    }
  };
  const scoped = (questionKey: string | undefined) => (questionKey === undefined ? {} : { questionKey });
  return {
    present: (presentation) => {
      try {
        controller?.present(presentation);
      } catch {
        // Idem.
      }
    },
    select: (value, questionKey) => dispatch({ type: 'select', value, ...scoped(questionKey) }),
    deselect: (value, questionKey) =>
      dispatch({ type: 'deselect', ...(value === undefined ? {} : { value }), ...scoped(questionKey) }),
    setText: (text, questionKey) => dispatch({ type: 'setText', text, ...scoped(questionKey) }),
    focusText: (questionKey) => dispatch({ type: 'focusText', ...scoped(questionKey) }),
    blurText: (questionKey) => dispatch({ type: 'blurText', ...scoped(questionKey) }),
    next: () => dispatch({ type: 'next' }),
    back: () => dispatch({ type: 'back' }),
    dismiss: (via = 'programmatic') => dispatch({ type: 'dismiss', via }),
    complete: () => dispatch({ type: 'complete' }),
  };
}

export function usePitacoSurvey(): UsePitacoSurveyResult {
  const context = useContext(PitacoContext);
  const controller = context?.controller ?? null;

  useEffect(() => {
    if (context === null) warnOutsideProvider('usePitacoSurvey');
  }, [context]);

  const snapshot = useSyncExternalStore(
    controller === null ? noopSubscribe : controller.subscribe,
    controller === null ? nullSnapshot : controller.getSnapshot,
    controller === null ? nullSnapshot : controller.getSnapshot,
  );
  const actions = useMemo(() => createSurveyActions(controller), [controller]);
  return useMemo(() => ({ ...toState(snapshot), ...actions }), [snapshot, actions]);
}
