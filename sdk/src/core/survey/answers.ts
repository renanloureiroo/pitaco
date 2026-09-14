import type { SurveyQuestion } from './schema';

// Valor de resposta enquanto a pessoa responde: texto (escolha única ou texto livre), inteiro
// (avaliação, escala, NPS) ou lista de textos (múltipla escolha).
export type AnswerValue = string | number | readonly string[];

// A mesma coisa na forma que vai para `POST /submission`.
export type AnswerValueWire = string | number | string[];

export const MAX_FREE_TEXT_LENGTH = 2000;

export function isAnswered(question: SurveyQuestion, value: AnswerValue | undefined): boolean {
  if (value === undefined) return false;
  switch (question.type) {
    case 'MULTIPLE_CHOICE':
      return Array.isArray(value) && value.length > 0;
    case 'FREE_TEXT':
      return typeof value === 'string' && value.trim() !== '';
    case 'SINGLE_CHOICE':
      return typeof value === 'string' && question.options.some((option) => option.value === value);
    case 'RATING':
    case 'SCALE':
    case 'NPS':
      return typeof value === 'number';
  }
}

// Se o valor escolhido é aceitável para a pergunta: o `value` de uma opção, ou um inteiro dentro
// da faixa. Nunca o rótulo.
export function acceptsChoice(question: SurveyQuestion, value: unknown): value is string | number {
  switch (question.type) {
    case 'SINGLE_CHOICE':
    case 'MULTIPLE_CHOICE':
      return typeof value === 'string' && question.options.some((option) => option.value === value);
    case 'RATING':
    case 'SCALE':
    case 'NPS': {
      const range = question.range;
      return (
        typeof value === 'number' &&
        Number.isInteger(value) &&
        range !== null &&
        value >= range.min &&
        value <= range.max
      );
    }
    case 'FREE_TEXT':
      return false;
  }
}

export function toWireValue(value: AnswerValue): AnswerValueWire {
  if (Array.isArray(value)) return Array.from(new Set(value as readonly string[]));
  return value as string | number;
}

export function textLength(value: AnswerValue | undefined): number {
  return typeof value === 'string' ? value.length : 0;
}
