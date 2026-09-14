// Avaliação local da condição, sem nova consulta (contrato, seção "Condição").
//
// Falha fechado: sem resposta à origem, com operador que não cabe no tipo, ou com número
// ilegível, a condição não é satisfeita e a pergunta vira não aplicável.

import { type AnswerValue, isAnswered } from './answers';
import type { QuestionCondition, SurveyQuestion } from './schema';

function parseInteger(value: string | undefined): number | null {
  if (value === undefined) return null;
  const trimmed = value.trim();
  return /^-?\d+$/.test(trimmed) ? Number(trimmed) : null;
}

export function isConditionSatisfied(
  condition: QuestionCondition,
  source: SurveyQuestion,
  answer: AnswerValue | undefined,
): boolean {
  if (answer === undefined || !isAnswered(source, answer)) return false;
  const { operator, values } = condition;
  const first = values[0];

  switch (source.type) {
    case 'MULTIPLE_CHOICE': {
      if (!Array.isArray(answer)) return false;
      const chosen = answer as readonly string[];
      if (operator === 'equals') return first !== undefined && chosen.includes(first);
      if (operator === 'not_equals') return first !== undefined && !chosen.includes(first);
      if (operator === 'in') return values.some((value) => chosen.includes(value));
      return false;
    }
    case 'SINGLE_CHOICE': {
      if (typeof answer !== 'string') return false;
      if (operator === 'equals') return first !== undefined && answer === first;
      if (operator === 'not_equals') return first !== undefined && answer !== first;
      if (operator === 'in') return values.includes(answer);
      return false;
    }
    case 'RATING':
    case 'SCALE':
    case 'NPS': {
      if (typeof answer !== 'number') return false;
      if (operator === 'between') {
        return (
          (condition.min === null || answer >= condition.min) &&
          (condition.max === null || answer <= condition.max)
        );
      }
      if (operator === 'in') return values.some((value) => parseInteger(value) === answer);
      const expected = parseInteger(first);
      if (expected === null) return false;
      return operator === 'equals' ? answer === expected : answer !== expected;
    }
    case 'FREE_TEXT':
      return false;
  }
}
