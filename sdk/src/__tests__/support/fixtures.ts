import { normalizeSurvey, type Survey } from '../../core/survey/schema';

// Chaves estáveis de pergunta (UUID, como no backend).
export const KEYS = {
  nps: '11111111-1111-4111-8111-111111111111',
  reason: '22222222-2222-4222-8222-222222222222',
  features: '33333333-3333-4333-8333-333333333333',
  rating: '44444444-4444-4444-8444-444444444444',
  comment: '55555555-5555-4555-8555-555555555555',
} as const;

export const SURVEY_ID = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
export const VERSION_ID = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';

// A pesquisa de referência dos testes: os seis tipos menos um (escala entra nos testes de
// condição), uma condição e o aviso de texto livre.
//   1. NPS, obrigatória
//   2. Escolha única, opcional, só com nota de 0 a 6 no NPS
//   3. Múltipla escolha, opcional
//   4. Avaliação de 1 a 5, obrigatória
//   5. Texto livre, opcional
export function deliverableSurvey(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    surveyId: SURVEY_ID,
    versionId: VERSION_ID,
    versionNumber: 3,
    freeTextNotice: { enabled: true, text: 'Evite escrever dados pessoais.' },
    questions: [
      {
        key: KEYS.nps,
        position: 1,
        statement: 'O quanto você recomendaria?',
        type: 'NPS',
        required: true,
        options: [],
        range: { min: 0, max: 10, minLabel: 'Nada provável', maxLabel: 'Muito provável' },
        condition: null,
      },
      {
        key: KEYS.reason,
        position: 2,
        statement: 'O que mais pesou?',
        type: 'SINGLE_CHOICE',
        required: false,
        options: [
          { label: 'Preço', value: 'preco', position: 1 },
          { label: 'Atendimento', value: 'atendimento', position: 2 },
        ],
        range: null,
        condition: { sourceKey: KEYS.nps, operator: 'between', values: [], min: 0, max: 6 },
      },
      {
        key: KEYS.features,
        position: 3,
        statement: 'O que você usa?',
        type: 'MULTIPLE_CHOICE',
        required: false,
        options: [
          { label: 'Pix', value: 'pix', position: 1 },
          { label: 'Cartão', value: 'cartao', position: 2 },
          { label: 'Boleto', value: 'boleto', position: 3 },
        ],
      },
      {
        key: KEYS.rating,
        position: 4,
        statement: 'Como avalia o app?',
        type: 'RATING',
        required: true,
        range: { min: 1, max: 5 },
      },
      {
        key: KEYS.comment,
        position: 5,
        statement: 'Quer contar mais?',
        type: 'FREE_TEXT',
        required: false,
      },
    ],
    ...overrides,
  };
}

export function referenceSurvey(overrides: Record<string, unknown> = {}): Survey {
  const parsed = normalizeSurvey(deliverableSurvey(overrides));
  if (parsed.kind !== 'survey') throw new Error('fixture inválida');
  return parsed.survey;
}
