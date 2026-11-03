// O caminho da pesquisa dado o conjunto de respostas: quais perguntas se aplicam, quais estão
// respondidas e qual é a próxima. Regra do contrato: origem pulada ou não aplicável não satisfaz
// condição nenhuma; condição que aponta para pergunta posterior, para si mesma ou para pergunta
// descartada também não.

import { type AnswerValue, isAnswered } from '../survey/answers';
import { isConditionSatisfied } from '../survey/condition';
import type { SurveyQuestion } from '../survey/schema';

export type PathStatus = 'ANSWERED' | 'UNANSWERED' | 'NOT_APPLICABLE';

export function resolveStatuses(
  questions: readonly SurveyQuestion[],
  answers: Readonly<Record<string, AnswerValue>>,
): PathStatus[] {
  const indexByKey = new Map(questions.map((question, index) => [question.key, index] as const));
  const statuses: PathStatus[] = [];

  questions.forEach((question, index) => {
    let applicable = true;
    if (question.condition !== null) {
      const sourceIndex = indexByKey.get(question.condition.sourceKey);
      const source = sourceIndex === undefined ? undefined : questions[sourceIndex];
      applicable =
        sourceIndex !== undefined &&
        source !== undefined &&
        sourceIndex < index &&
        statuses[sourceIndex] === 'ANSWERED' &&
        isConditionSatisfied(question.condition, source, answers[source.key]);
    }
    statuses.push(
      !applicable ? 'NOT_APPLICABLE' : isAnswered(question, answers[question.key]) ? 'ANSWERED' : 'UNANSWERED',
    );
  });

  return statuses;
}

export interface NextStep {
  // -1 quando não há pergunta aplicável depois de `from`.
  readonly index: number;
  // As perguntas puladas pela condição entre `from` e `index`.
  readonly passed: readonly number[];
}

export function findNextApplicable(
  questions: readonly SurveyQuestion[],
  answers: Readonly<Record<string, AnswerValue>>,
  from: number,
): NextStep {
  const statuses = resolveStatuses(questions, answers);
  const passed: number[] = [];
  for (let index = from + 1; index < questions.length; index += 1) {
    if (statuses[index] === 'NOT_APPLICABLE') {
      passed.push(index);
    } else {
      return { index, passed };
    }
  }
  return { index: -1, passed };
}
