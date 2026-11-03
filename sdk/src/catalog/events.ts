// Catálogo de eventos de interação, versão 1.
//
// É o único lugar do SDK que define os tipos, e é fechado: o app hospedeiro ouve os eventos em
// `onEvent`, mas não cria tipo novo nem altera payload. Mudar este arquivo é mudança de contrato
// com o backend (`InteractionEventType` no OpenAPI): tipo, campo ou valor novo exige
// `CATALOG_VERSION` nova. O teste de contrato compara esta lista com o snapshot do OpenAPI.

export const CATALOG_VERSION = 1 as const;
export type CatalogVersion = typeof CATALOG_VERSION;

export const INTERACTION_EVENT_TYPES = [
  'survey_presented',
  'question_viewed',
  'answer_selected',
  'answer_changed',
  'answer_deselected',
  'text_focused',
  'text_edited',
  'text_blurred',
  'validation_blocked',
  'question_skipped',
  'question_not_applicable',
  'navigated_next',
  'navigated_back',
  'question_left',
  'survey_backgrounded',
  'survey_foregrounded',
  'survey_dismissed',
  'survey_completed',
] as const;

export type InteractionEventType = (typeof INTERACTION_EVENT_TYPES)[number];

// Eventos que dizem respeito a uma pergunta levam `questionKey`; os demais, nunca.
export const QUESTION_EVENT_TYPES = [
  'question_viewed',
  'answer_selected',
  'answer_changed',
  'answer_deselected',
  'text_focused',
  'text_edited',
  'text_blurred',
  'validation_blocked',
  'question_skipped',
  'question_not_applicable',
  'navigated_next',
  'navigated_back',
  'question_left',
] as const satisfies readonly InteractionEventType[];

export type QuestionEventType = (typeof QUESTION_EVENT_TYPES)[number];
export type SurveyEventType = Exclude<InteractionEventType, QuestionEventType>;

export const PRESENTATIONS = ['bottom-sheet', 'modal', 'inline'] as const;
export type Presentation = (typeof PRESENTATIONS)[number];

export const DISMISS_VIAS = [
  'close_button',
  'swipe',
  'backdrop',
  'hardware_back',
  'navigation',
  'programmatic',
] as const;
export type DismissVia = (typeof DISMISS_VIAS)[number];

export type QuestionViewedFrom = 'start' | 'next' | 'back';
export type QuestionLeftTo = 'next' | 'back' | 'dismiss' | 'complete';

// O `value` da opção ou o número escolhido, nunca o rótulo e nunca texto livre.
export type AnswerEventValue = string | number;

export type EmptyData = Record<string, never>;

export interface InteractionEventDataMap {
  survey_presented: {
    presentation: Presentation;
    questionCount: number;
    renderableCount: number;
    triggerEvent: string;
  };
  question_viewed: { position: number; visit: number; from: QuestionViewedFrom };
  answer_selected: { value: AnswerEventValue };
  answer_changed: { from: AnswerEventValue; to: AnswerEventValue };
  answer_deselected: { value: AnswerEventValue };
  text_focused: EmptyData;
  text_edited: { length: number };
  text_blurred: { length: number };
  validation_blocked: { reason: 'required_missing' };
  question_skipped: EmptyData;
  question_not_applicable: { sourceKey: string };
  navigated_next: { toKey: string };
  navigated_back: { toKey: string };
  question_left: {
    visit: number;
    to: QuestionLeftTo;
    durationMs: number;
    activeMs: number;
    answered: boolean;
  };
  survey_backgrounded: EmptyData;
  survey_foregrounded: { backgroundMs: number };
  survey_dismissed: { via: DismissVia; position: number; answeredCount: number };
  survey_completed: {
    answeredCount: number;
    skippedCount: number;
    notApplicableCount: number;
    activeMs: number;
  };
}

// Os campos de `data` de cada tipo, na ordem do contrato. Serve ao teste de contrato e a quem
// quer montar uma tabela dos eventos (painel de depuração, documentação).
export const INTERACTION_EVENT_DATA_FIELDS: {
  readonly [T in InteractionEventType]: readonly (keyof InteractionEventDataMap[T])[];
} = {
  survey_presented: ['presentation', 'questionCount', 'renderableCount', 'triggerEvent'],
  question_viewed: ['position', 'visit', 'from'],
  answer_selected: ['value'],
  answer_changed: ['from', 'to'],
  answer_deselected: ['value'],
  text_focused: [],
  text_edited: ['length'],
  text_blurred: ['length'],
  validation_blocked: ['reason'],
  question_skipped: [],
  question_not_applicable: ['sourceKey'],
  navigated_next: ['toKey'],
  navigated_back: ['toKey'],
  question_left: ['visit', 'to', 'durationMs', 'activeMs', 'answered'],
  survey_backgrounded: [],
  survey_foregrounded: ['backgroundMs'],
  survey_dismissed: ['via', 'position', 'answeredCount'],
  survey_completed: ['answeredCount', 'skippedCount', 'notApplicableCount', 'activeMs'],
};

interface Envelope<T extends InteractionEventType> {
  readonly catalogVersion: CatalogVersion;
  readonly type: T;
  readonly displayId: string;
  readonly seq: number;
  readonly occurredAt: string;
  readonly elapsedMs: number;
  readonly data: Readonly<InteractionEventDataMap[T]>;
}

export type InteractionEventOf<T extends InteractionEventType> = T extends QuestionEventType
  ? Envelope<T> & { readonly questionKey: string }
  : Envelope<T>;

export type InteractionEvent = {
  [T in InteractionEventType]: InteractionEventOf<T>;
}[InteractionEventType];

// Nome da superfície pública: o evento de interação que o app recebe em `onEvent`.
export type PitacoEvent = InteractionEvent;

const interactionTypes: ReadonlySet<string> = new Set(INTERACTION_EVENT_TYPES);
const questionTypes: ReadonlySet<string> = new Set(QUESTION_EVENT_TYPES);

export function isInteractionEventType(value: unknown): value is InteractionEventType {
  return typeof value === 'string' && interactionTypes.has(value);
}

export function isQuestionEventType(value: unknown): value is QuestionEventType {
  return typeof value === 'string' && questionTypes.has(value);
}

export function isInteractionEvent(value: unknown): value is InteractionEvent {
  return (
    typeof value === 'object' &&
    value !== null &&
    'type' in value &&
    'catalogVersion' in value &&
    isInteractionEventType((value as { type: unknown }).type)
  );
}

export function isPresentation(value: unknown): value is Presentation {
  return typeof value === 'string' && (PRESENTATIONS as readonly string[]).includes(value);
}

export function isDismissVia(value: unknown): value is DismissVia {
  return typeof value === 'string' && (DISMISS_VIAS as readonly string[]).includes(value);
}
