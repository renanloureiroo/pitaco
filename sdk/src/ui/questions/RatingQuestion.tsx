// Avaliação: estrelas desenhadas só com `Text`/`View` (glifo `★`/`☆`, nenhuma imagem externa).
// `value` é sempre o número da faixa (`question.range.min..max`), nunca o rótulo — a n-ésima
// estrela representa `range.min + (n - 1)`. Rótulo de acessibilidade "X de N" por estrela, papel
// `radio` (é uma escolha única de um valor entre `total`).

import type { ReactNode } from 'react';
import { Pressable, View } from 'react-native';
import { MIN_TOUCH_TARGET } from '../primitives/PitacoButton';
import { PitacoText } from '../primitives/PitacoText';
import type { QuestionRendererProps } from '../types';
import { QuestionErrorFrame } from './shared/QuestionErrorFrame';

const FILLED_STAR = '★';
const EMPTY_STAR = '☆';

export function RatingQuestion(props: QuestionRendererProps): ReactNode {
  const { question, value, error, actions, theme, strings } = props;
  const range = question.range;
  if (range === null) return null;

  const selected = typeof value === 'number' ? value : undefined;
  const total = range.max - range.min + 1;
  const stars: number[] = [];
  for (let star = range.min; star <= range.max; star += 1) stars.push(star);

  return (
    <QuestionErrorFrame error={error} message={strings.validationRequired} theme={theme}>
      <View style={{ gap: theme.tokens.spacing.sm }}>
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: theme.tokens.spacing.xs }}>
          {stars.map((star, index) => {
            const position = index + 1;
            const filled = selected !== undefined && star <= selected;
            return (
              <Pressable
                key={star}
                accessibilityRole="radio"
                accessibilityState={{ checked: selected === star }}
                accessibilityLabel={`${position} de ${total}`}
                onPress={() => actions.select(star)}
                style={{
                  minWidth: MIN_TOUCH_TARGET,
                  minHeight: MIN_TOUCH_TARGET,
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <PitacoText
                  variant="title"
                  color={filled ? theme.tokens.colors.primary : theme.tokens.colors.textSecondary}
                >
                  {filled ? FILLED_STAR : EMPTY_STAR}
                </PitacoText>
              </Pressable>
            );
          })}
        </View>
        {range.minLabel !== null || range.maxLabel !== null ? (
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between' }}>
            <PitacoText variant="caption" color={theme.tokens.colors.textSecondary}>
              {range.minLabel ?? ''}
            </PitacoText>
            <PitacoText variant="caption" color={theme.tokens.colors.textSecondary}>
              {range.maxLabel ?? ''}
            </PitacoText>
          </View>
        ) : null}
      </View>
    </QuestionErrorFrame>
  );
}
