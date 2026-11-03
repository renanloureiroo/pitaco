// A visão da sessão que a UI consome (`usePitacoSurvey`). Derivada do estado da máquina; nunca é
// fonte de verdade.

import type { AnswerValue } from '../survey/answers';
import { isAnswered } from '../survey/answers';
import type { FreeTextNotice, SurveyQuestion } from '../survey/schema';
import type { MachineState, SessionStatus, ValidationError } from '../machine/machine';
import { findNextApplicable, type PathStatus, resolveStatuses } from '../machine/path';

export interface SurveySummary {
  readonly surveyId: string;
  readonly versionId: string;
  readonly versionNumber: number;
  readonly questionCount: number;
  readonly renderableCount: number;
  readonly freeTextNotice: FreeTextNotice;
}

export interface SurveyProgress {
  // Posição da pergunta atual no caminho percorrido, a partir de 1.
  readonly position: number;
  // Estimativa do total com as respostas atuais: pode diminuir quando uma condição pula perguntas.
  readonly total: number;
}

export interface SurveySnapshot {
  readonly status: SessionStatus;
  readonly displayId: string;
  readonly survey: SurveySummary;
  readonly questions: readonly SurveyQuestion[];
  // A pergunta a renderizar. Em `ready` é a primeira, para o contêiner montar antes de ficar
  // visível; depois do desfecho é nula.
  readonly question: SurveyQuestion | null;
  readonly answers: Readonly<Record<string, AnswerValue>>;
  readonly value: AnswerValue | undefined;
  readonly progress: SurveyProgress;
  readonly validationError: ValidationError | null;
  readonly focusedQuestionKey: string | null;
  readonly canGoBack: boolean;
  readonly canGoNext: boolean;
  readonly isLast: boolean;
}

// Perguntas adiante que ainda podem aparecer. Uma condição cuja origem é a pergunta atual sem
// resposta, ou uma pergunta adiante, ainda não está decidida e conta como possível.
function countAhead(
  questions: readonly SurveyQuestion[],
  statuses: readonly PathStatus[],
  index: number,
  currentAnswered: boolean,
): number {
  const indexByKey = new Map(questions.map((question, position) => [question.key, position] as const));
  let count = 0;
  for (let position = index + 1; position < questions.length; position += 1) {
    if (statuses[position] !== 'NOT_APPLICABLE') {
      count += 1;
      continue;
    }
    const source = indexByKey.get(questions[position]?.condition?.sourceKey ?? '');
    const undecided = source !== undefined && (source > index || (source === index && !currentAnswered));
    if (undecided) count += 1;
  }
  return count;
}

export function deriveSnapshot(state: MachineState): SurveySnapshot {
  const { survey, answers } = state;
  const questions = survey.questions;

  let index = -1;
  if (state.status === 'presented') index = state.index;
  else if (state.status === 'ready') index = findNextApplicable(questions, answers, -1).index;
  const question = questions[index] ?? null;

  let total = 0;
  let isLast = false;
  if (question !== null) {
    const statuses = resolveStatuses(questions, answers);
    total = state.trail.length + 1 + countAhead(questions, statuses, index, isAnswered(question, answers[question.key]));
    isLast = findNextApplicable(questions, answers, index).index === -1;
  }

  const active = state.status === 'presented' && question !== null;
  const value = question === null ? undefined : answers[question.key];

  return {
    status: state.status,
    displayId: state.displayId,
    survey: {
      surveyId: survey.surveyId,
      versionId: survey.versionId,
      versionNumber: survey.versionNumber,
      questionCount: survey.questionCount,
      renderableCount: questions.length,
      freeTextNotice: survey.freeTextNotice,
    },
    questions,
    question,
    answers,
    value,
    progress: { position: question === null ? 0 : state.trail.length + 1, total },
    validationError: state.validationError,
    focusedQuestionKey: state.focusedKey,
    canGoBack: active && state.trail.length > 0,
    canGoNext: active && (!question.required || isAnswered(question, value)),
    isLast,
  };
}
