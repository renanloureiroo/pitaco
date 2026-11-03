// Máquina de estado da pesquisa: a única forma de mudar o estado de uma exibição e a única
// emissora dos eventos do catálogo. Função pura: estado + ação + instante → novo estado +
// eventos. Qualquer UI (a padrão, um renderizador substituído ou uma UI headless) chega aqui pelas
// mesmas ações e por isso produz o mesmo fluxo de eventos.
//
// Ordem dos eventos numa transição (fixa, coberta por teste):
//   next:     text_edited? → question_skipped? → text_blurred? → navigated_next → question_left
//             → question_not_applicable* → question_viewed
//   back:     text_edited? → text_blurred? → navigated_back → question_left → question_viewed
//   complete: text_edited? → question_skipped? → text_blurred? → question_left
//             → question_not_applicable* → survey_completed
//   dismiss:  text_edited? → text_blurred? → question_left → survey_dismissed
// `next` na última pergunta aplicável conclui. Tentar avançar ou concluir com obrigatória em
// branco emite só `validation_blocked` (depois de um `text_edited` pendente).

import {
  CATALOG_VERSION,
  type DismissVia,
  type InteractionEvent,
  type InteractionEventDataMap,
  type InteractionEventType,
  isDismissVia,
  type Presentation,
  type QuestionLeftTo,
  type QuestionViewedFrom,
} from '../../catalog/events';
import { type Instant, toIsoString } from '../clock';
import {
  acceptsChoice,
  type AnswerValue,
  isAnswered,
  MAX_FREE_TEXT_LENGTH,
  textLength,
} from '../survey/answers';
import type { Survey, SurveyQuestion } from '../survey/schema';
import { findNextApplicable, resolveStatuses } from './path';

export const TEXT_EDIT_DEBOUNCE_MS = 1000;

// ready: recebida e pronta, contêiner ainda não visível. presented: visível. completed e
// dismissed: desfecho. discarded: dispensada antes de ficar visível (nunca virou exibição).
export type SessionStatus = 'ready' | 'presented' | 'completed' | 'dismissed' | 'discarded';

export interface ValidationError {
  readonly questionKey: string;
  readonly reason: 'required_missing';
}

export interface PendingTextEdit {
  readonly questionKey: string;
  readonly dueAt: number;
}

export interface MachineState {
  readonly status: SessionStatus;
  readonly survey: Survey;
  readonly displayId: string;
  readonly triggerEvent: string;
  readonly presentation: Presentation | null;
  readonly index: number;
  readonly trail: readonly number[];
  readonly answers: Readonly<Record<string, AnswerValue>>;
  readonly visits: Readonly<Record<string, number>>;
  readonly passedOver: Readonly<Record<string, true>>;
  readonly presentedAt: number;
  readonly visitStartedAt: number;
  readonly visitBackgroundMs: number;
  readonly backgroundedAt: number | null;
  readonly totalBackgroundMs: number;
  readonly seq: number;
  readonly lastElapsedMs: number;
  readonly validationError: ValidationError | null;
  readonly focusedKey: string | null;
  readonly pendingEdit: PendingTextEdit | null;
  readonly dismissedVia: DismissVia | null;
}

// `questionKey` opcional nas ações de resposta: se vier e não for a pergunta atual, a ação é
// ignorada (um renderizador atrasado nunca responde a pergunta errada).
type Scoped = { readonly questionKey?: string };

export type SurveyAction =
  | { readonly type: 'present'; readonly presentation: Presentation }
  | ({ readonly type: 'select'; readonly value: string | number } & Scoped)
  | ({ readonly type: 'deselect'; readonly value?: string | number } & Scoped)
  | ({ readonly type: 'setText'; readonly text: string } & Scoped)
  | ({ readonly type: 'focusText' } & Scoped)
  | ({ readonly type: 'blurText' } & Scoped)
  | { readonly type: 'next' }
  | { readonly type: 'back' }
  | { readonly type: 'dismiss'; readonly via: DismissVia }
  | { readonly type: 'complete' }
  | { readonly type: 'background' }
  | { readonly type: 'foreground' }
  | { readonly type: 'tick' };

export interface TransitionResult {
  readonly state: MachineState;
  readonly events: readonly InteractionEvent[];
}

export function createInitialState(input: {
  readonly survey: Survey;
  readonly displayId: string;
  readonly triggerEvent: string;
}): MachineState {
  return {
    status: 'ready',
    survey: input.survey,
    displayId: input.displayId,
    triggerEvent: input.triggerEvent,
    presentation: null,
    index: -1,
    trail: [],
    answers: {},
    visits: {},
    passedOver: {},
    presentedAt: 0,
    visitStartedAt: 0,
    visitBackgroundMs: 0,
    backgroundedAt: null,
    totalBackgroundMs: 0,
    seq: 0,
    lastElapsedMs: 0,
    validationError: null,
    focusedKey: null,
    pendingEdit: null,
    dismissedVia: null,
  };
}

export function transition(state: MachineState, action: SurveyAction, now: Instant): TransitionResult {
  if (action.type === 'present') {
    if (state.status !== 'ready') return { state, events: [] };
    const run = new Run(
      { ...state, status: 'presented', presentation: action.presentation, presentedAt: now.mono },
      now,
    );
    run.present();
    return run.result(state);
  }

  if (state.status === 'ready' && action.type === 'dismiss') {
    return { state: { ...state, status: 'discarded' }, events: [] };
  }
  if (state.status !== 'presented') return { state, events: [] };

  const run = new Run(state, now);
  run.flushDueEdit();
  switch (action.type) {
    case 'select':
      run.select(action.value, action.questionKey);
      break;
    case 'deselect':
      run.deselect(action.value, action.questionKey);
      break;
    case 'setText':
      run.setText(action.text, action.questionKey);
      break;
    case 'focusText':
      run.focusText(action.questionKey);
      break;
    case 'blurText':
      run.blurText(action.questionKey);
      break;
    case 'next':
      run.next();
      break;
    case 'back':
      run.back();
      break;
    case 'complete':
      run.complete();
      break;
    case 'dismiss':
      run.dismiss(action.via);
      break;
    case 'background':
      run.background();
      break;
    case 'foreground':
      run.foreground();
      break;
    case 'tick':
      break;
  }
  return run.result(state);
}

type Draft = { -readonly [K in keyof MachineState]: MachineState[K] };

class Run {
  private readonly draft: Draft;
  private readonly events: InteractionEvent[] = [];

  constructor(
    state: MachineState,
    private readonly now: Instant,
  ) {
    this.draft = { ...state };
  }

  result(original: MachineState): TransitionResult {
    if (this.events.length === 0 && shallowEqual(original, this.draft)) {
      return { state: original, events: [] };
    }
    return { state: this.draft, events: this.events };
  }

  private get questions(): readonly SurveyQuestion[] {
    return this.draft.survey.questions;
  }

  private get current(): SurveyQuestion | undefined {
    return this.questions[this.draft.index];
  }

  private emit<T extends InteractionEventType>(
    type: T,
    data: InteractionEventDataMap[T],
    questionKey?: string,
  ) {
    const draft = this.draft;
    draft.seq += 1;
    const elapsed = Math.max(draft.lastElapsedMs, Math.round(this.now.mono - draft.presentedAt), 0);
    draft.lastElapsedMs = elapsed;
    this.events.push({
      catalogVersion: CATALOG_VERSION,
      type,
      displayId: draft.displayId,
      seq: draft.seq,
      occurredAt: toIsoString(this.now.wall),
      elapsedMs: elapsed,
      ...(questionKey === undefined ? {} : { questionKey }),
      data,
    } as unknown as InteractionEvent);
  }

  present() {
    const { survey, triggerEvent } = this.draft;
    this.emit('survey_presented', {
      presentation: this.draft.presentation ?? 'bottom-sheet',
      questionCount: survey.questionCount,
      renderableCount: survey.questions.length,
      triggerEvent,
    });
    const first = findNextApplicable(this.questions, this.draft.answers, -1);
    this.passOver(first.passed);
    if (first.index === -1) {
      this.finishCompleted();
    } else {
      this.enter(first.index, 'start');
    }
  }

  select(value: string | number, scope: string | undefined) {
    const question = this.scoped(scope);
    if (!question || question.type === 'FREE_TEXT' || !acceptsChoice(question, value)) return;
    const previous = this.draft.answers[question.key];

    if (question.type === 'MULTIPLE_CHOICE') {
      const chosen = Array.isArray(previous) ? (previous as readonly string[]) : [];
      if (chosen.includes(value as string)) return;
      this.setAnswer(question.key, [...chosen, value as string]);
      this.emit('answer_selected', { value }, question.key);
    } else {
      if (previous === value) return;
      this.setAnswer(question.key, value);
      if (previous === undefined || Array.isArray(previous)) {
        this.emit('answer_selected', { value }, question.key);
      } else {
        this.emit('answer_changed', { from: previous as string | number, to: value }, question.key);
      }
    }
    this.clearValidationIfAnswered(question);
  }

  deselect(value: string | number | undefined, scope: string | undefined) {
    const question = this.scoped(scope);
    if (!question || question.type === 'FREE_TEXT') return;
    const previous = this.draft.answers[question.key];

    if (question.type === 'MULTIPLE_CHOICE') {
      const chosen = Array.isArray(previous) ? (previous as readonly string[]) : [];
      if (typeof value !== 'string' || !chosen.includes(value)) return;
      const remaining = chosen.filter((item) => item !== value);
      this.setAnswer(question.key, remaining.length > 0 ? remaining : undefined);
      this.emit('answer_deselected', { value }, question.key);
      return;
    }

    if (previous === undefined || Array.isArray(previous)) return;
    if (value !== undefined && value !== previous) return;
    this.setAnswer(question.key, undefined);
    this.emit('answer_deselected', { value: previous as string | number }, question.key);
  }

  setText(text: string, scope: string | undefined) {
    const question = this.scoped(scope);
    if (!question || question.type !== 'FREE_TEXT' || typeof text !== 'string') return;
    const next = text.slice(0, MAX_FREE_TEXT_LENGTH);
    const previous = this.draft.answers[question.key];
    if (next === (typeof previous === 'string' ? previous : '')) return;
    this.setAnswer(question.key, next === '' ? undefined : next);
    this.draft.pendingEdit = { questionKey: question.key, dueAt: this.now.mono + TEXT_EDIT_DEBOUNCE_MS };
    this.clearValidationIfAnswered(question);
  }

  focusText(scope: string | undefined) {
    const question = this.scoped(scope);
    if (!question || question.type !== 'FREE_TEXT' || this.draft.focusedKey === question.key) return;
    this.draft.focusedKey = question.key;
    this.emit('text_focused', {}, question.key);
  }

  blurText(scope: string | undefined) {
    const question = this.scoped(scope);
    if (!question || this.draft.focusedKey !== question.key) return;
    this.flushEdit();
    this.releaseFocus();
  }

  next() {
    const question = this.current;
    if (!question) return;
    this.flushEdit();
    if (this.blockIfRequiredMissing(question)) return;

    const step = findNextApplicable(this.questions, this.draft.answers, this.draft.index);
    const target = this.questions[step.index];
    if (!target) {
      this.complete();
      return;
    }

    if (!isAnswered(question, this.draft.answers[question.key])) {
      this.emit('question_skipped', {}, question.key);
    }
    this.releaseFocus();
    this.emit('navigated_next', { toKey: target.key }, question.key);
    this.leave('next');
    this.draft.trail = [...this.draft.trail, this.draft.index];
    this.passOver(step.passed);
    this.enter(step.index, 'next');
  }

  back() {
    const question = this.current;
    const targetIndex = this.draft.trail[this.draft.trail.length - 1];
    const target = targetIndex === undefined ? undefined : this.questions[targetIndex];
    if (!question || targetIndex === undefined || !target) return;

    this.flushEdit();
    this.releaseFocus();
    this.emit('navigated_back', { toKey: target.key }, question.key);
    this.leave('back');
    this.draft.trail = this.draft.trail.slice(0, -1);
    this.enter(targetIndex, 'back');
  }

  complete() {
    const question = this.current;
    if (!question) return;
    this.flushEdit();
    if (this.blockIfRequiredMissing(question)) return;

    const step = findNextApplicable(this.questions, this.draft.answers, this.draft.index);
    // Concluir antes da última pergunta aplicável não existe: a ação é ignorada.
    if (step.index !== -1) return;

    if (!isAnswered(question, this.draft.answers[question.key])) {
      this.emit('question_skipped', {}, question.key);
    }
    this.releaseFocus();
    this.leave('complete');
    this.passOver(step.passed);
    this.finishCompleted();
  }

  dismiss(via: DismissVia) {
    const question = this.current;
    if (!question) return;
    const safeVia: DismissVia = isDismissVia(via) ? via : 'programmatic';

    this.flushEdit();
    this.releaseFocus();
    this.leave('dismiss');
    const statuses = resolveStatuses(this.questions, this.draft.answers);
    this.emit('survey_dismissed', {
      via: safeVia,
      position: question.position,
      answeredCount: statuses.filter((status) => status === 'ANSWERED').length,
    });
    this.draft.status = 'dismissed';
    this.draft.dismissedVia = safeVia;
    this.draft.pendingEdit = null;
  }

  background() {
    if (this.draft.backgroundedAt !== null) return;
    this.draft.backgroundedAt = this.now.mono;
    this.emit('survey_backgrounded', {});
  }

  foreground() {
    const since = this.draft.backgroundedAt;
    if (since === null) return;
    const mono = this.now.mono;
    this.draft.visitBackgroundMs += Math.max(0, mono - Math.max(since, this.draft.visitStartedAt));
    this.draft.totalBackgroundMs += Math.max(0, mono - since);
    this.draft.backgroundedAt = null;
    this.emit('survey_foregrounded', { backgroundMs: Math.max(0, Math.round(mono - since)) });
  }

  flushDueEdit() {
    const pending = this.draft.pendingEdit;
    if (pending !== null && this.now.mono >= pending.dueAt) this.flushEdit();
  }

  private flushEdit() {
    const pending = this.draft.pendingEdit;
    if (pending === null) return;
    this.draft.pendingEdit = null;
    this.emit('text_edited', { length: textLength(this.draft.answers[pending.questionKey]) }, pending.questionKey);
  }

  private releaseFocus() {
    const key = this.draft.focusedKey;
    if (key === null) return;
    this.draft.focusedKey = null;
    this.emit('text_blurred', { length: textLength(this.draft.answers[key]) }, key);
  }

  private scoped(scope: string | undefined): SurveyQuestion | undefined {
    const question = this.current;
    if (!question || (scope !== undefined && scope !== question.key)) return undefined;
    return question;
  }

  private setAnswer(key: string, value: AnswerValue | undefined) {
    const answers = { ...this.draft.answers };
    if (value === undefined) {
      delete answers[key];
    } else {
      answers[key] = value;
    }
    this.draft.answers = answers;
  }

  private clearValidationIfAnswered(question: SurveyQuestion) {
    if (
      this.draft.validationError?.questionKey === question.key &&
      isAnswered(question, this.draft.answers[question.key])
    ) {
      this.draft.validationError = null;
    }
  }

  private blockIfRequiredMissing(question: SurveyQuestion): boolean {
    if (!question.required || isAnswered(question, this.draft.answers[question.key])) return false;
    this.draft.validationError = { questionKey: question.key, reason: 'required_missing' };
    this.emit('validation_blocked', { reason: 'required_missing' }, question.key);
    return true;
  }

  private enter(index: number, from: QuestionViewedFrom) {
    const question = this.questions[index];
    if (!question) return;
    const draft = this.draft;
    const visit = (draft.visits[question.key] ?? 0) + 1;
    draft.index = index;
    draft.visits = { ...draft.visits, [question.key]: visit };
    draft.visitStartedAt = this.now.mono;
    draft.visitBackgroundMs = 0;
    draft.validationError = null;
    draft.focusedKey = null;
    this.emit('question_viewed', { position: question.position, visit, from }, question.key);
  }

  private leave(to: QuestionLeftTo) {
    const question = this.current;
    if (!question) return;
    const draft = this.draft;
    const mono = this.now.mono;
    let background = draft.visitBackgroundMs;
    if (draft.backgroundedAt !== null) {
      background += Math.max(0, mono - Math.max(draft.backgroundedAt, draft.visitStartedAt));
    }
    const durationMs = Math.max(0, Math.round(mono - draft.visitStartedAt));
    const activeMs = Math.min(durationMs, Math.max(0, Math.round(mono - draft.visitStartedAt - background)));
    this.emit(
      'question_left',
      {
        visit: draft.visits[question.key] ?? 1,
        to,
        durationMs,
        activeMs,
        answered: isAnswered(question, draft.answers[question.key]),
      },
      question.key,
    );
  }

  private passOver(indices: readonly number[]) {
    for (const index of indices) {
      const question = this.questions[index];
      if (!question) continue;
      this.draft.passedOver = { ...this.draft.passedOver, [question.key]: true };
      this.emit('question_not_applicable', { sourceKey: question.condition?.sourceKey ?? '' }, question.key);
    }
  }

  private finishCompleted() {
    const draft = this.draft;
    const statuses = resolveStatuses(this.questions, draft.answers);
    const mono = this.now.mono;
    const pendingBackground = draft.backgroundedAt === null ? 0 : Math.max(0, mono - draft.backgroundedAt);
    const activeMs = Math.max(0, Math.round(mono - draft.presentedAt - draft.totalBackgroundMs - pendingBackground));
    this.emit('survey_completed', {
      answeredCount: statuses.filter((status) => status === 'ANSWERED').length,
      skippedCount: statuses.filter((status) => status === 'UNANSWERED').length,
      notApplicableCount: statuses.filter((status) => status === 'NOT_APPLICABLE').length,
      activeMs,
    });
    draft.status = 'completed';
    draft.pendingEdit = null;
    draft.focusedKey = null;
  }
}

function shallowEqual(left: MachineState, right: MachineState): boolean {
  const keys = Object.keys(left) as (keyof MachineState)[];
  return keys.every((key) => Object.is(left[key], right[key]));
}
