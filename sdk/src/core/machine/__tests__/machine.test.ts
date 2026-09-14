import {
  DISMISS_VIAS,
  type InteractionEvent,
  INTERACTION_EVENT_TYPES,
  isQuestionEventType,
} from '../../../catalog/events';
import { createFakeClock } from '../../../__tests__/support/fakeClock';
import { KEYS, referenceSurvey } from '../../../__tests__/support/fixtures';
import {
  createMachineDriver,
  DISMISS_SCRIPT,
  runScript,
  signature,
  STANDARD_SCRIPT,
} from '../../../__tests__/support/script';
import { normalizeSurvey, type Survey } from '../../survey/schema';
import { createInitialState, type MachineState, type SurveyAction, transition } from '../machine';
import { buildSubmission } from '../submission';

const DISPLAY_ID = 'd0000000-0000-4000-8000-000000000000';

function setup(survey: Survey = referenceSurvey()) {
  const clock = createFakeClock();
  let state: MachineState = createInitialState({ survey, displayId: DISPLAY_ID, triggerEvent: 'checkout_completed' });
  const all: InteractionEvent[] = [];
  const apply = (action: SurveyAction) => {
    const result = transition(state, action, clock.now());
    state = result.state;
    all.push(...result.events);
    return result.events;
  };
  return {
    clock,
    apply,
    all,
    get state() {
      return state;
    },
    types: (events: readonly InteractionEvent[]) =>
      events.map((event) => ('questionKey' in event ? `${event.type}@${event.questionKey}` : event.type)),
  };
}

function present(machine: ReturnType<typeof setup>) {
  return machine.apply({ type: 'present', presentation: 'bottom-sheet' });
}

function customSurvey(questions: unknown[]): Survey {
  const parsed = normalizeSurvey({ surveyId: 's', versionId: 'v', versionNumber: 1, questions });
  if (parsed.kind !== 'survey') throw new Error('fixture inválida');
  return parsed.survey;
}

describe('máquina de estado: apresentação', () => {
  it('present emite survey_presented e a primeira question_viewed, com seq a partir de 1', () => {
    const machine = setup();
    const events = present(machine);
    expect(events).toEqual([
      {
        catalogVersion: 1,
        type: 'survey_presented',
        displayId: DISPLAY_ID,
        seq: 1,
        occurredAt: '2026-09-12T13:00:00.000Z',
        elapsedMs: 0,
        data: { presentation: 'bottom-sheet', questionCount: 5, renderableCount: 5, triggerEvent: 'checkout_completed' },
      },
      {
        catalogVersion: 1,
        type: 'question_viewed',
        displayId: DISPLAY_ID,
        seq: 2,
        occurredAt: '2026-09-12T13:00:00.000Z',
        elapsedMs: 0,
        questionKey: KEYS.nps,
        data: { position: 1, visit: 1, from: 'start' },
      },
    ]);
    expect(machine.state.status).toBe('presented');
    expect(present(machine)).toEqual([]);
  });

  it('antes de ficar visível nenhuma ação emite evento, e dispensar só descarta', () => {
    const machine = setup();
    expect(machine.apply({ type: 'select', value: 9 })).toEqual([]);
    expect(machine.apply({ type: 'next' })).toEqual([]);
    expect(machine.apply({ type: 'background' })).toEqual([]);
    expect(machine.apply({ type: 'dismiss', via: 'swipe' })).toEqual([]);
    expect(machine.state.status).toBe('discarded');
    expect(present(machine)).toEqual([]);
    expect(buildSubmission(machine.state)).toBeNull();
  });
});

describe('máquina de estado: respostas', () => {
  it('answer_selected na primeira escolha, answer_changed na troca e answer_deselected ao limpar', () => {
    const machine = setup();
    present(machine);
    expect(machine.apply({ type: 'select', value: 9 }).map((event) => event.data)).toEqual([{ value: 9 }]);
    expect(machine.apply({ type: 'select', value: 9 })).toEqual([]);
    const changed = machine.apply({ type: 'select', value: 3 });
    expect(changed.map((event) => [event.type, event.data])).toEqual([['answer_changed', { from: 9, to: 3 }]]);
    const cleared = machine.apply({ type: 'deselect' });
    expect(cleared.map((event) => [event.type, event.data])).toEqual([['answer_deselected', { value: 3 }]]);
    expect(machine.apply({ type: 'deselect' })).toEqual([]);
    expect(machine.state.answers).toEqual({});
  });

  it('múltipla escolha: cada opção adicionada é answer_selected e cada removida é answer_deselected', () => {
    const machine = setup();
    present(machine);
    machine.apply({ type: 'select', value: 10 });
    machine.apply({ type: 'next' });
    const events = [
      ...machine.apply({ type: 'select', value: 'pix' }),
      ...machine.apply({ type: 'select', value: 'pix' }),
      ...machine.apply({ type: 'select', value: 'boleto' }),
      ...machine.apply({ type: 'deselect', value: 'pix' }),
      ...machine.apply({ type: 'deselect', value: 'cartao' }),
    ];
    expect(events.map((event) => [event.type, event.data])).toEqual([
      ['answer_selected', { value: 'pix' }],
      ['answer_selected', { value: 'boleto' }],
      ['answer_deselected', { value: 'pix' }],
    ]);
    expect(machine.state.answers[KEYS.features]).toEqual(['boleto']);
  });

  it('valor fora da pergunta é ignorado: rótulo, fora da faixa, fração, e escolha em texto livre', () => {
    const machine = setup();
    present(machine);
    expect(machine.apply({ type: 'select', value: 11 })).toEqual([]);
    expect(machine.apply({ type: 'select', value: 3.5 })).toEqual([]);
    expect(machine.apply({ type: 'select', value: 'Nada provável' })).toEqual([]);
    expect(machine.apply({ type: 'select', value: 5, questionKey: KEYS.rating })).toEqual([]);
    expect(machine.apply({ type: 'setText', text: 'oi' })).toEqual([]);
    expect(machine.state.answers).toEqual({});
  });

  it('validation_blocked ao avançar com obrigatória em branco, e responder limpa o erro', () => {
    const machine = setup();
    present(machine);
    const blocked = machine.apply({ type: 'next' });
    expect(machine.types(blocked)).toEqual([`validation_blocked@${KEYS.nps}`]);
    expect(blocked[0]?.data).toEqual({ reason: 'required_missing' });
    expect(machine.state.validationError).toEqual({ questionKey: KEYS.nps, reason: 'required_missing' });
    expect(machine.state.index).toBe(0);
    machine.apply({ type: 'select', value: 7 });
    expect(machine.state.validationError).toBeNull();
  });
});

describe('máquina de estado: navegação e condição', () => {
  it('next: navigated_next, question_left, question_not_applicable da pulada e question_viewed, nessa ordem', () => {
    const machine = setup();
    present(machine);
    machine.apply({ type: 'select', value: 9 });
    machine.clock.advance(2000);
    const events = machine.apply({ type: 'next' });
    expect(machine.types(events)).toEqual([
      `navigated_next@${KEYS.nps}`,
      `question_left@${KEYS.nps}`,
      `question_not_applicable@${KEYS.reason}`,
      `question_viewed@${KEYS.features}`,
    ]);
    expect(events.map((event) => event.data)).toEqual([
      { toKey: KEYS.features },
      { visit: 1, to: 'next', durationMs: 2000, activeMs: 2000, answered: true },
      { sourceKey: KEYS.nps },
      { position: 3, visit: 1, from: 'next' },
    ]);
  });

  it('com a condição satisfeita, a pergunta condicionada aparece', () => {
    const machine = setup();
    present(machine);
    machine.apply({ type: 'select', value: 3 });
    const events = machine.apply({ type: 'next' });
    expect(machine.types(events)).toEqual([
      `navigated_next@${KEYS.nps}`,
      `question_left@${KEYS.nps}`,
      `question_viewed@${KEYS.reason}`,
    ]);
  });

  it('question_skipped ao avançar opcional em branco, antes de navigated_next', () => {
    const machine = setup();
    present(machine);
    machine.apply({ type: 'select', value: 3 });
    machine.apply({ type: 'next' });
    const events = machine.apply({ type: 'next' });
    expect(machine.types(events)).toEqual([
      `question_skipped@${KEYS.reason}`,
      `navigated_next@${KEYS.reason}`,
      `question_left@${KEYS.reason}`,
      `question_viewed@${KEYS.features}`,
    ]);
    expect(events[2]?.data).toMatchObject({ answered: false });
  });

  it('back: cada visita tem os próprios tempos e o número da visita cresce', () => {
    const machine = setup();
    present(machine);
    machine.apply({ type: 'select', value: 9 });
    machine.clock.advance(1000);
    machine.apply({ type: 'next' });
    machine.clock.advance(500);
    const back = machine.apply({ type: 'back' });
    expect(machine.types(back)).toEqual([
      `navigated_back@${KEYS.features}`,
      `question_left@${KEYS.features}`,
      `question_viewed@${KEYS.nps}`,
    ]);
    expect(back.map((event) => event.data)).toEqual([
      { toKey: KEYS.nps },
      { visit: 1, to: 'back', durationMs: 500, activeMs: 500, answered: false },
      { position: 1, visit: 2, from: 'back' },
    ]);
    machine.clock.advance(700);
    const forward = machine.apply({ type: 'next' });
    expect(forward[1]?.data).toEqual({ visit: 2, to: 'next', durationMs: 700, activeMs: 700, answered: true });
    expect(forward[3]?.data).toEqual({ position: 3, visit: 2, from: 'next' });
  });

  it('voltar e corrigir a origem torna aplicável a pergunta que antes foi pulada', () => {
    const machine = setup();
    present(machine);
    machine.apply({ type: 'select', value: 9 });
    machine.apply({ type: 'next' });
    machine.apply({ type: 'back' });
    machine.apply({ type: 'select', value: 2 });
    const events = machine.apply({ type: 'next' });
    expect(machine.types(events).at(-1)).toBe(`question_viewed@${KEYS.reason}`);
  });

  it('back na primeira pergunta não faz nada', () => {
    const machine = setup();
    present(machine);
    expect(machine.apply({ type: 'back' })).toEqual([]);
  });
});

describe('máquina de estado: texto livre', () => {
  function atComment() {
    const machine = setup();
    present(machine);
    machine.apply({ type: 'select', value: 9 });
    machine.apply({ type: 'next' });
    machine.apply({ type: 'next' });
    machine.apply({ type: 'select', value: 5 });
    machine.apply({ type: 'next' });
    expect(machine.state.survey.questions[machine.state.index]?.key).toBe(KEYS.comment);
    return machine;
  }

  it('text_focused, text_edited com debounce de 1 s e text_blurred com o tamanho, nunca o texto', () => {
    const machine = atComment();
    expect(machine.types(machine.apply({ type: 'focusText' }))).toEqual([`text_focused@${KEYS.comment}`]);
    expect(machine.apply({ type: 'focusText' })).toEqual([]);
    expect(machine.apply({ type: 'setText', text: 'o' })).toEqual([]);
    machine.clock.advance(500);
    expect(machine.apply({ type: 'setText', text: 'ok' })).toEqual([]);
    machine.clock.advance(999);
    expect(machine.apply({ type: 'tick' })).toEqual([]);
    machine.clock.advance(1);
    const edited = machine.apply({ type: 'tick' });
    expect(edited.map((event) => [event.type, event.data])).toEqual([['text_edited', { length: 2 }]]);
    expect(machine.apply({ type: 'tick' })).toEqual([]);

    machine.apply({ type: 'setText', text: 'ok!' });
    const blurred = machine.apply({ type: 'blurText' });
    expect(blurred.map((event) => [event.type, event.data])).toEqual([
      ['text_edited', { length: 3 }],
      ['text_blurred', { length: 3 }],
    ]);
    expect(JSON.stringify(machine.all)).not.toContain('ok!');
  });

  it('sair da pergunta com o campo em foco fecha o foco antes de navegar', () => {
    const machine = atComment();
    machine.apply({ type: 'focusText' });
    machine.apply({ type: 'setText', text: 'abc' });
    const events = machine.apply({ type: 'back' });
    expect(machine.types(events)).toEqual([
      `text_edited@${KEYS.comment}`,
      `text_blurred@${KEYS.comment}`,
      `navigated_back@${KEYS.comment}`,
      `question_left@${KEYS.comment}`,
      `question_viewed@${KEYS.rating}`,
    ]);
    expect(machine.apply({ type: 'blurText' })).toEqual([]);
  });

  it('o texto é limitado a 2000 caracteres', () => {
    const machine = atComment();
    machine.apply({ type: 'setText', text: 'x'.repeat(2500) });
    expect(machine.state.answers[KEYS.comment]).toHaveLength(2000);
  });
});

describe('máquina de estado: segundo plano', () => {
  it('survey_backgrounded e survey_foregrounded, e activeMs desconta o tempo em segundo plano', () => {
    const machine = setup();
    present(machine);
    machine.clock.advance(1000);
    expect(machine.types(machine.apply({ type: 'background' }))).toEqual(['survey_backgrounded']);
    expect(machine.apply({ type: 'background' })).toEqual([]);
    machine.clock.advance(3000);
    const foreground = machine.apply({ type: 'foreground' });
    expect(foreground.map((event) => [event.type, event.data])).toEqual([['survey_foregrounded', { backgroundMs: 3000 }]]);
    expect(machine.apply({ type: 'foreground' })).toEqual([]);
    machine.clock.advance(500);
    machine.apply({ type: 'select', value: 9 });
    const left = machine.apply({ type: 'next' }).find((event) => event.type === 'question_left');
    expect(left?.data).toEqual({ visit: 1, to: 'next', durationMs: 4500, activeMs: 1500, answered: true });
  });

  it('sair da pergunta ainda em segundo plano conta o tempo corrido como inativo', () => {
    const machine = setup();
    present(machine);
    machine.clock.advance(1000);
    machine.apply({ type: 'background' });
    machine.clock.advance(2000);
    const dismissed = machine.apply({ type: 'dismiss', via: 'programmatic' });
    expect(dismissed[0]?.data).toEqual({ visit: 1, to: 'dismiss', durationMs: 3000, activeMs: 1000, answered: false });
  });
});

describe('máquina de estado: dispensa', () => {
  it.each(DISMISS_VIAS)('registra a via "%s"', (via) => {
    const machine = setup();
    present(machine);
    const events = machine.apply({ type: 'dismiss', via });
    expect(events.at(-1)).toMatchObject({ type: 'survey_dismissed', data: { via, position: 1, answeredCount: 0 } });
    expect(machine.state.status).toBe('dismissed');
  });

  it('via fora do catálogo vira programmatic', () => {
    const machine = setup();
    present(machine);
    const events = machine.apply({ type: 'dismiss', via: 'shake' as never });
    expect(events.at(-1)?.data).toMatchObject({ via: 'programmatic' });
  });

  it('dispensa na segunda pergunta preservando a primeira: question_left e survey_dismissed, e depois nada', () => {
    const machine = setup();
    present(machine);
    machine.apply({ type: 'select', value: 8 });
    machine.apply({ type: 'next' });
    const events = machine.apply({ type: 'dismiss', via: 'close_button' });
    expect(machine.types(events)).toEqual([`question_left@${KEYS.features}`, 'survey_dismissed']);
    expect(events.map((event) => event.data)).toEqual([
      { visit: 1, to: 'dismiss', durationMs: 0, activeMs: 0, answered: false },
      { via: 'close_button', position: 3, answeredCount: 1 },
    ]);
    expect(machine.apply({ type: 'select', value: 'pix' })).toEqual([]);
    expect(machine.apply({ type: 'next' })).toEqual([]);
    expect(buildSubmission(machine.state)).toEqual({
      outcome: 'DISMISSED',
      answers: [
        { questionKey: KEYS.nps, status: 'ANSWERED', value: 8 },
        { questionKey: KEYS.reason, status: 'NOT_APPLICABLE' },
        { questionKey: KEYS.features, status: 'SKIPPED' },
      ],
    });
  });
});

describe('máquina de estado: conclusão', () => {
  it('complete emite question_left, a não aplicável do fim e survey_completed com as contagens', () => {
    const survey = customSurvey([
      { key: 'a', position: 1, statement: 'A', type: 'SCALE', required: true, range: { min: 1, max: 5 } },
      {
        key: 'b',
        position: 2,
        statement: 'B',
        type: 'FREE_TEXT',
        condition: { sourceKey: 'a', operator: 'equals', values: ['1'] },
      },
    ]);
    const machine = setup(survey);
    present(machine);
    machine.apply({ type: 'select', value: 4 });
    machine.clock.advance(1200);
    const events = machine.apply({ type: 'next' });
    expect(machine.types(events)).toEqual(['question_left@a', 'question_not_applicable@b', 'survey_completed']);
    expect(events[0]?.data).toMatchObject({ to: 'complete' });
    expect(events[2]?.data).toEqual({ answeredCount: 1, skippedCount: 0, notApplicableCount: 1, activeMs: 1200 });
    expect(buildSubmission(machine.state)).toEqual({
      outcome: 'COMPLETED',
      answers: [
        { questionKey: 'a', status: 'ANSWERED', value: 4 },
        { questionKey: 'b', status: 'NOT_APPLICABLE' },
      ],
    });
  });

  it('complete antes da última pergunta aplicável é ignorado', () => {
    const machine = setup();
    present(machine);
    machine.apply({ type: 'select', value: 9 });
    expect(machine.apply({ type: 'complete' })).toEqual([]);
    expect(machine.state.status).toBe('presented');
  });

  it('complete com obrigatória em branco é bloqueado', () => {
    const survey = customSurvey([{ key: 'a', position: 1, statement: 'A', type: 'NPS', required: true }]);
    const machine = setup(survey);
    present(machine);
    expect(machine.types(machine.apply({ type: 'complete' }))).toEqual(['validation_blocked@a']);
  });
});

describe('máquina de estado: roteiro completo', () => {
  async function run(script: typeof STANDARD_SCRIPT) {
    const machine = createMachineDriver(referenceSurvey());
    await runScript(machine.driver, script);
    return { events: machine.events, state: machine.state };
  }

  it('produz a sequência esperada, na ordem', async () => {
    const { events } = await run(STANDARD_SCRIPT);
    expect(events.map((event) => ('questionKey' in event ? `${event.type}@${event.questionKey.slice(0, 1)}` : event.type))).toEqual([
      'survey_presented',
      'question_viewed@1',
      'validation_blocked@1',
      'answer_selected@1',
      'answer_changed@1',
      'navigated_next@1',
      'question_left@1',
      'question_viewed@2',
      'answer_selected@2',
      'answer_deselected@2',
      'question_skipped@2',
      'navigated_next@2',
      'question_left@2',
      'question_viewed@3',
      'answer_selected@3',
      'answer_selected@3',
      'navigated_back@3',
      'question_left@3',
      'question_viewed@2',
      'question_skipped@2',
      'navigated_next@2',
      'question_left@2',
      'question_viewed@3',
      'survey_backgrounded',
      'survey_foregrounded',
      'answer_deselected@3',
      'navigated_next@3',
      'question_left@3',
      'question_viewed@4',
      'answer_selected@4',
      'navigated_next@4',
      'question_left@4',
      'question_viewed@5',
      'text_focused@5',
      'text_edited@5',
      'text_edited@5',
      'text_blurred@5',
      'question_left@5',
      'survey_completed',
    ]);
    const featuresSecondVisit = events.filter(
      (event) => event.type === 'question_left' && event.questionKey === KEYS.features,
    )[1];
    expect(featuresSecondVisit?.data).toEqual({ visit: 2, to: 'next', durationMs: 3000, activeMs: 0, answered: true });
    expect(events.at(-1)?.data).toMatchObject({ answeredCount: 4, skippedCount: 1, notApplicableCount: 0 });
  });

  it('o roteiro completo e o de dispensa cobrem os 18 tipos do catálogo', async () => {
    const standard = await run(STANDARD_SCRIPT);
    const dismiss = await run(DISMISS_SCRIPT);
    const covered = new Set([...standard.events, ...dismiss.events].map((event) => event.type));
    expect([...covered].sort()).toEqual([...INTERACTION_EVENT_TYPES].sort());
  });

  it('envelope válido em todo evento: seq de 1 em diante, elapsedMs monotônico, questionKey só nos de pergunta', async () => {
    for (const script of [STANDARD_SCRIPT, DISMISS_SCRIPT]) {
      const { events } = await run(script);
      events.forEach((event, index) => {
        expect(event.seq).toBe(index + 1);
        expect(event.catalogVersion).toBe(1);
        expect(event.displayId).toBe(DISPLAY_ID);
        expect(Number.isNaN(Date.parse(event.occurredAt))).toBe(false);
        expect(Number.isInteger(event.elapsedMs)).toBe(true);
        if (index > 0) expect(event.elapsedMs).toBeGreaterThanOrEqual(events[index - 1]!.elapsedMs);
        expect('questionKey' in event).toBe(isQuestionEventType(event.type));
      });
    }
  });

  it('submissão da conclusão: respondida, pulada e o valor de cada tipo', async () => {
    const { state } = await run(STANDARD_SCRIPT);
    expect(buildSubmission(state)).toEqual({
      outcome: 'COMPLETED',
      answers: [
        { questionKey: KEYS.nps, status: 'ANSWERED', value: 3 },
        { questionKey: KEYS.reason, status: 'SKIPPED' },
        { questionKey: KEYS.features, status: 'ANSWERED', value: ['boleto'] },
        { questionKey: KEYS.rating, status: 'ANSWERED', value: 4 },
        { questionKey: KEYS.comment, status: 'ANSWERED', value: 'ok!' },
      ],
    });
  });

  it('assinatura dos eventos ignora só os tempos', async () => {
    const { events } = await run(DISMISS_SCRIPT);
    expect(signature(events)).toEqual([
      `1 survey_presented {"presentation":"bottom-sheet","questionCount":5,"renderableCount":5,"triggerEvent":"checkout_completed"}`,
      `2 question_viewed@${KEYS.nps} {"position":1,"visit":1,"from":"start"}`,
      `3 answer_selected@${KEYS.nps} {"value":10}`,
      `4 navigated_next@${KEYS.nps} {"toKey":"${KEYS.features}"}`,
      `5 question_left@${KEYS.nps} {"visit":1,"to":"next","answered":true}`,
      `6 question_not_applicable@${KEYS.reason} {"sourceKey":"${KEYS.nps}"}`,
      `7 question_viewed@${KEYS.features} {"position":3,"visit":1,"from":"next"}`,
      `8 answer_selected@${KEYS.features} {"value":"cartao"}`,
      `9 question_left@${KEYS.features} {"visit":1,"to":"dismiss","answered":true}`,
      `10 survey_dismissed {"via":"swipe","position":3,"answeredCount":2}`,
    ]);
  });
});
