// Normalização do schema da pesquisa recebido na elegibilidade.
//
// Regras do contrato: tipo de pergunta desconhecido é pulado em silêncio; campo desconhecido é
// ignorado; `null` e ausente valem o mesmo. Pergunta de tipo conhecido mas com forma quebrada
// (sem opções, faixa inválida, condição ilegível) também sai, e é contada à parte para o
// relatório de erro, porque é defeito do servidor e não versão mais nova.

export const QUESTION_TYPES = [
  'SINGLE_CHOICE',
  'MULTIPLE_CHOICE',
  'RATING',
  'SCALE',
  'NPS',
  'FREE_TEXT',
] as const;
export type QuestionType = (typeof QUESTION_TYPES)[number];

export const CONDITION_OPERATORS = ['equals', 'not_equals', 'in', 'between'] as const;
export type ConditionOperator = (typeof CONDITION_OPERATORS)[number];

export interface ChoiceOption {
  readonly label: string;
  readonly value: string;
  readonly position: number;
}

export interface NumericRange {
  readonly min: number;
  readonly max: number;
  readonly minLabel: string | null;
  readonly maxLabel: string | null;
}

export interface QuestionCondition {
  readonly sourceKey: string;
  readonly operator: ConditionOperator;
  readonly values: readonly string[];
  readonly min: number | null;
  readonly max: number | null;
}

export interface SurveyQuestion {
  readonly key: string;
  readonly position: number;
  readonly statement: string;
  readonly type: QuestionType;
  readonly required: boolean;
  readonly options: readonly ChoiceOption[];
  readonly range: NumericRange | null;
  readonly condition: QuestionCondition | null;
}

export interface FreeTextNotice {
  readonly enabled: boolean;
  readonly text: string | null;
}

export interface Survey {
  readonly surveyId: string;
  readonly versionId: string;
  readonly versionNumber: number;
  readonly freeTextNotice: FreeTextNotice;
  // Só as renderizáveis, na ordem de `position`.
  readonly questions: readonly SurveyQuestion[];
  // Quantas perguntas a versão tem no servidor, inclusive as descartadas.
  readonly questionCount: number;
  readonly unknownQuestionTypes: readonly string[];
  readonly malformedQuestionCount: number;
}

export type SurveyParse =
  | { readonly kind: 'survey'; readonly survey: Survey }
  | { readonly kind: 'malformed'; readonly reason: string };

export type EligibilityParse = { readonly kind: 'none' } | SurveyParse;

const MAX_OPTION_VALUE = 120;
const MAX_REPORTED_TYPE = 40;
const MAX_REPORTED_TYPES = 20;
const MAX_SCALE_SPAN = 100;
const NPS_RANGE = { min: 0, max: 10 } as const;

const questionTypes: ReadonlySet<string> = new Set(QUESTION_TYPES);
const operators: ReadonlySet<string> = new Set(CONDITION_OPERATORS);

export function isQuestionType(value: unknown): value is QuestionType {
  return typeof value === 'string' && questionTypes.has(value);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isInteger(value: unknown): value is number {
  return typeof value === 'number' && Number.isInteger(value);
}

function nonEmptyString(value: unknown): string | null {
  return typeof value === 'string' && value.trim() !== '' ? value : null;
}

function optionalString(value: unknown): string | null {
  return typeof value === 'string' ? value : null;
}

export function parseEligibilityResponse(body: unknown): EligibilityParse {
  if (!isRecord(body)) return { kind: 'malformed', reason: 'body_not_object' };
  if (body.survey === undefined || body.survey === null) return { kind: 'none' };
  return normalizeSurvey(body.survey);
}

export function normalizeSurvey(raw: unknown): SurveyParse {
  if (!isRecord(raw)) return { kind: 'malformed', reason: 'survey_not_object' };

  const surveyId = nonEmptyString(raw.surveyId);
  const versionId = nonEmptyString(raw.versionId);
  if (surveyId === null || versionId === null) return { kind: 'malformed', reason: 'survey_identity' };
  if (!Array.isArray(raw.questions)) return { kind: 'malformed', reason: 'questions_not_array' };

  const unknownQuestionTypes: string[] = [];
  const questions: SurveyQuestion[] = [];
  const keys = new Set<string>();
  let malformedQuestionCount = 0;

  raw.questions.forEach((item: unknown, index: number) => {
    const parsed = normalizeQuestion(item, index);
    if (parsed.kind === 'unknown') {
      if (!unknownQuestionTypes.includes(parsed.type)) unknownQuestionTypes.push(parsed.type);
      return;
    }
    if (parsed.kind === 'malformed' || keys.has(parsed.question.key)) {
      malformedQuestionCount += 1;
      return;
    }
    keys.add(parsed.question.key);
    questions.push(parsed.question);
  });

  questions.sort((left, right) => left.position - right.position);

  return {
    kind: 'survey',
    survey: {
      surveyId,
      versionId,
      versionNumber: isInteger(raw.versionNumber) ? raw.versionNumber : 0,
      freeTextNotice: normalizeNotice(raw.freeTextNotice),
      questions,
      questionCount: raw.questions.length,
      unknownQuestionTypes: unknownQuestionTypes.slice(0, MAX_REPORTED_TYPES),
      malformedQuestionCount,
    },
  };
}

function normalizeNotice(raw: unknown): FreeTextNotice {
  if (!isRecord(raw)) return { enabled: false, text: null };
  const text = optionalString(raw.text);
  return { enabled: raw.enabled === true && text !== null && text.trim() !== '', text };
}

type QuestionParse =
  | { readonly kind: 'ok'; readonly question: SurveyQuestion }
  | { readonly kind: 'unknown'; readonly type: string }
  | { readonly kind: 'malformed' };

const MALFORMED: QuestionParse = { kind: 'malformed' };

function normalizeQuestion(raw: unknown, index: number): QuestionParse {
  if (!isRecord(raw)) return MALFORMED;
  if (typeof raw.type !== 'string') return MALFORMED;
  if (!isQuestionType(raw.type)) {
    const type = raw.type.trim().slice(0, MAX_REPORTED_TYPE);
    return { kind: 'unknown', type: type === '' ? 'UNKNOWN' : type };
  }

  const type = raw.type;
  const key = nonEmptyString(raw.key);
  const statement = optionalString(raw.statement);
  if (key === null || statement === null) return MALFORMED;

  let options: readonly ChoiceOption[] = [];
  let range: NumericRange | null = null;

  if (type === 'SINGLE_CHOICE' || type === 'MULTIPLE_CHOICE') {
    options = normalizeOptions(raw.options);
    if (options.length === 0) return MALFORMED;
  } else if (type === 'RATING' || type === 'SCALE') {
    range = normalizeRange(raw.range);
    if (range === null) return MALFORMED;
  } else if (type === 'NPS') {
    const labels = normalizeRange(raw.range);
    range = { ...NPS_RANGE, minLabel: labels?.minLabel ?? null, maxLabel: labels?.maxLabel ?? null };
  }

  let condition: QuestionCondition | null = null;
  if (raw.condition !== undefined && raw.condition !== null) {
    condition = normalizeCondition(raw.condition);
    if (condition === null) return MALFORMED;
  }

  return {
    kind: 'ok',
    question: {
      key,
      position: isInteger(raw.position) && raw.position >= 1 ? raw.position : index + 1,
      statement,
      type,
      required: raw.required === true,
      options,
      range,
      condition,
    },
  };
}

function normalizeOptions(raw: unknown): readonly ChoiceOption[] {
  if (!Array.isArray(raw)) return [];
  const seen = new Set<string>();
  const options: ChoiceOption[] = [];
  raw.forEach((item: unknown, index: number) => {
    if (!isRecord(item)) return;
    const value = nonEmptyString(item.value);
    const label = optionalString(item.label);
    if (value === null || label === null || value.length > MAX_OPTION_VALUE || seen.has(value)) return;
    seen.add(value);
    options.push({ label, value, position: isInteger(item.position) ? item.position : index + 1 });
  });
  return options.sort((left, right) => left.position - right.position);
}

function normalizeRange(raw: unknown): NumericRange | null {
  if (!isRecord(raw)) return null;
  if (!isInteger(raw.min) || !isInteger(raw.max)) return null;
  if (raw.min >= raw.max || raw.max - raw.min > MAX_SCALE_SPAN) return null;
  return {
    min: raw.min,
    max: raw.max,
    minLabel: optionalString(raw.minLabel),
    maxLabel: optionalString(raw.maxLabel),
  };
}

function normalizeCondition(raw: unknown): QuestionCondition | null {
  if (!isRecord(raw)) return null;
  const sourceKey = nonEmptyString(raw.sourceKey);
  if (sourceKey === null || typeof raw.operator !== 'string' || !operators.has(raw.operator)) {
    return null;
  }
  const operator = raw.operator as ConditionOperator;
  const values = Array.isArray(raw.values)
    ? raw.values.filter((value: unknown): value is string => typeof value === 'string')
    : [];
  const min = isInteger(raw.min) ? raw.min : null;
  const max = isInteger(raw.max) ? raw.max : null;

  if (operator === 'between' ? min === null && max === null : values.length === 0) return null;
  return { sourceKey, operator, values, min, max };
}
