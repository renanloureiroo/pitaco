import { isConditionSatisfied } from '../condition';
import type { QuestionCondition, QuestionType, SurveyQuestion } from '../schema';

function question(type: QuestionType): SurveyQuestion {
  return {
    key: 'origem',
    position: 1,
    statement: 'Origem',
    type,
    required: false,
    options:
      type === 'SINGLE_CHOICE' || type === 'MULTIPLE_CHOICE'
        ? [
            { label: 'A', value: 'a', position: 1 },
            { label: 'B', value: 'b', position: 2 },
            { label: 'C', value: 'c', position: 3 },
          ]
        : [],
    range: type === 'SCALE' ? { min: 1, max: 7, minLabel: null, maxLabel: null } : null,
    condition: null,
  };
}

const when = (operator: QuestionCondition['operator'], values: string[], min: number | null = null, max: number | null = null) =>
  ({ sourceKey: 'origem', operator, values, min, max }) as QuestionCondition;

describe('avaliação local da condição', () => {
  it('escolha única: equals, not_equals e in', () => {
    const source = question('SINGLE_CHOICE');
    expect(isConditionSatisfied(when('equals', ['a']), source, 'a')).toBe(true);
    expect(isConditionSatisfied(when('equals', ['a']), source, 'b')).toBe(false);
    expect(isConditionSatisfied(when('not_equals', ['a']), source, 'b')).toBe(true);
    expect(isConditionSatisfied(when('in', ['b', 'c']), source, 'c')).toBe(true);
    expect(isConditionSatisfied(when('in', ['b', 'c']), source, 'a')).toBe(false);
    expect(isConditionSatisfied(when('between', [], 0, 1), source, 'a')).toBe(false);
  });

  it('múltipla escolha: equals é "contém", not_equals é "não contém", in é "contém algum"', () => {
    const source = question('MULTIPLE_CHOICE');
    expect(isConditionSatisfied(when('equals', ['b']), source, ['a', 'b'])).toBe(true);
    expect(isConditionSatisfied(when('equals', ['c']), source, ['a', 'b'])).toBe(false);
    expect(isConditionSatisfied(when('not_equals', ['c']), source, ['a', 'b'])).toBe(true);
    expect(isConditionSatisfied(when('not_equals', ['a']), source, ['a', 'b'])).toBe(false);
    expect(isConditionSatisfied(when('in', ['c', 'b']), source, ['a', 'b'])).toBe(true);
    expect(isConditionSatisfied(when('in', ['c']), source, ['a', 'b'])).toBe(false);
  });

  it('numéricas: between inclusivo, e equals, not_equals e in com números escritos como texto', () => {
    const source = question('SCALE');
    expect(isConditionSatisfied(when('between', [], 2, 4), source, 2)).toBe(true);
    expect(isConditionSatisfied(when('between', [], 2, 4), source, 4)).toBe(true);
    expect(isConditionSatisfied(when('between', [], 2, 4), source, 5)).toBe(false);
    expect(isConditionSatisfied(when('between', [], null, 3), source, 1)).toBe(true);
    expect(isConditionSatisfied(when('equals', ['3']), source, 3)).toBe(true);
    expect(isConditionSatisfied(when('not_equals', ['3']), source, 4)).toBe(true);
    expect(isConditionSatisfied(when('in', ['1', '7']), source, 7)).toBe(true);
  });

  it('falha fechado: sem resposta, número ilegível ou origem de texto livre', () => {
    expect(isConditionSatisfied(when('equals', ['a']), question('SINGLE_CHOICE'), undefined)).toBe(false);
    expect(isConditionSatisfied(when('equals', ['a']), question('MULTIPLE_CHOICE'), [])).toBe(false);
    expect(isConditionSatisfied(when('equals', ['três']), question('SCALE'), 3)).toBe(false);
    expect(isConditionSatisfied(when('not_equals', ['três']), question('SCALE'), 3)).toBe(false);
    expect(isConditionSatisfied(when('equals', ['x']), question('FREE_TEXT'), 'x')).toBe(false);
  });
});
