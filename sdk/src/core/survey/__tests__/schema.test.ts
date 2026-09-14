import { deliverableSurvey, KEYS } from '../../../__tests__/support/fixtures';
import { normalizeSurvey, parseEligibilityResponse } from '../schema';

function survey(overrides: Record<string, unknown> = {}) {
  const parsed = normalizeSurvey(deliverableSurvey(overrides));
  if (parsed.kind !== 'survey') throw new Error(`esperava pesquisa, veio ${parsed.reason}`);
  return parsed.survey;
}

describe('resposta da elegibilidade', () => {
  it('survey nulo ou ausente é "não há pesquisa"', () => {
    expect(parseEligibilityResponse({ survey: null })).toEqual({ kind: 'none' });
    expect(parseEligibilityResponse({})).toEqual({ kind: 'none' });
  });

  it('corpo que não é objeto é malformado', () => {
    expect(parseEligibilityResponse('<html>')).toEqual({ kind: 'malformed', reason: 'body_not_object' });
    expect(parseEligibilityResponse([])).toEqual({ kind: 'malformed', reason: 'body_not_object' });
    expect(parseEligibilityResponse(null)).toEqual({ kind: 'malformed', reason: 'body_not_object' });
  });

  it('pesquisa sem identidade ou sem lista de perguntas é malformada', () => {
    expect(parseEligibilityResponse({ survey: { versionId: 'v', questions: [] } })).toMatchObject({
      kind: 'malformed',
      reason: 'survey_identity',
    });
    expect(parseEligibilityResponse({ survey: { surveyId: 's', versionId: 'v', questions: 'x' } })).toMatchObject({
      kind: 'malformed',
      reason: 'questions_not_array',
    });
  });
});

describe('normalização do schema', () => {
  it('lê a pesquisa de referência com os cinco tipos e a condição', () => {
    const parsed = survey();
    expect(parsed.questions.map((question) => question.type)).toEqual([
      'NPS',
      'SINGLE_CHOICE',
      'MULTIPLE_CHOICE',
      'RATING',
      'FREE_TEXT',
    ]);
    expect(parsed.questionCount).toBe(5);
    expect(parsed.freeTextNotice).toEqual({ enabled: true, text: 'Evite escrever dados pessoais.' });
    expect(parsed.questions[1]?.condition).toEqual({
      sourceKey: KEYS.nps,
      operator: 'between',
      values: [],
      min: 0,
      max: 6,
    });
  });

  it('pula em silêncio o tipo desconhecido e guarda o tipo para a supressão', () => {
    const base = deliverableSurvey();
    const parsed = survey({
      questions: [
        ...(base.questions as unknown[]),
        { key: 'x', position: 6, statement: 'Matriz', type: 'MATRIX', required: true },
        { key: 'y', position: 7, statement: 'Outra', type: 'MATRIX' },
      ],
    });
    expect(parsed.questions).toHaveLength(5);
    expect(parsed.questionCount).toBe(7);
    expect(parsed.unknownQuestionTypes).toEqual(['MATRIX']);
    expect(parsed.malformedQuestionCount).toBe(0);
  });

  it('ignora campo desconhecido em pergunta e opção conhecidas', () => {
    const parsed = survey({
      questions: [
        {
          key: KEYS.reason,
          position: 1,
          statement: 'Motivo',
          type: 'SINGLE_CHOICE',
          required: false,
          layout: 'carrossel',
          options: [{ label: 'Preço', value: 'preco', position: 1, icon: 'moeda' }],
        },
      ],
    });
    expect(parsed.questions[0]).toEqual({
      key: KEYS.reason,
      position: 1,
      statement: 'Motivo',
      type: 'SINGLE_CHOICE',
      required: false,
      options: [{ label: 'Preço', value: 'preco', position: 1 }],
      range: null,
      condition: null,
    });
  });

  it('ordena por position e trata null e ausente do mesmo jeito', () => {
    const parsed = survey({
      freeTextNotice: null,
      questions: [
        { key: 'b', position: 2, statement: 'B', type: 'FREE_TEXT', required: null, condition: null },
        { key: 'a', position: 1, statement: 'A', type: 'FREE_TEXT' },
      ],
    });
    expect(parsed.questions.map((question) => question.key)).toEqual(['a', 'b']);
    expect(parsed.questions[1]?.required).toBe(false);
    expect(parsed.freeTextNotice).toEqual({ enabled: false, text: null });
  });

  it('NPS é sempre de 0 a 10, com os rótulos que vierem', () => {
    const parsed = survey({
      questions: [{ key: 'n', position: 1, statement: 'NPS', type: 'NPS', range: { min: 1, max: 5, minLabel: 'Não' } }],
    });
    expect(parsed.questions[0]?.range).toEqual({ min: 0, max: 10, minLabel: 'Não', maxLabel: null });
  });

  it('descarta pergunta conhecida com forma quebrada e conta à parte', () => {
    const parsed = survey({
      questions: [
        { key: 'ok', position: 1, statement: 'Ok', type: 'FREE_TEXT' },
        { key: 'sem-opcoes', position: 2, statement: 'x', type: 'SINGLE_CHOICE', options: [] },
        { key: 'faixa', position: 3, statement: 'x', type: 'RATING', range: { min: 5, max: 1 } },
        { key: 'condicao', position: 4, statement: 'x', type: 'FREE_TEXT', condition: { operator: 'equals' } },
        { key: 'ok', position: 5, statement: 'repetida', type: 'FREE_TEXT' },
        { position: 6, statement: 'sem chave', type: 'FREE_TEXT' },
      ],
    });
    expect(parsed.questions.map((question) => question.key)).toEqual(['ok']);
    expect(parsed.malformedQuestionCount).toBe(5);
  });

  it('descarta opção sem value, com value longo demais ou repetida', () => {
    const parsed = survey({
      questions: [
        {
          key: 'm',
          position: 1,
          statement: 'M',
          type: 'MULTIPLE_CHOICE',
          options: [
            { label: 'A', value: 'a', position: 2 },
            { label: 'B', value: 'b', position: 1 },
            { label: 'A de novo', value: 'a', position: 3 },
            { label: 'Sem valor', position: 4 },
            { label: 'Longo', value: 'x'.repeat(121), position: 5 },
          ],
        },
      ],
    });
    expect(parsed.questions[0]?.options.map((option) => option.value)).toEqual(['b', 'a']);
  });
});
