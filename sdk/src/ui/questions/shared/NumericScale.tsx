// Compartilhado por `SCALE` e `NPS`: uma faixa de números tocáveis, que quebra linha sozinha em
// tela estreita (`flexWrap`), com os rótulos das pontas quando existem.

import type { ReactNode } from 'react';
import { View } from 'react-native';
import type { NumericRange } from '../../../core/survey/schema';
import { PitacoScalePoint } from '../../primitives/PitacoScalePoint';
import { PitacoText } from '../../primitives/PitacoText';
import type { PitacoResolvedTheme } from '../../theme/tokens';

export interface NumericScaleProps {
  readonly range: NumericRange;
  readonly value: number | undefined;
  readonly onSelect: (value: number) => void;
  readonly theme: PitacoResolvedTheme;
}

export function NumericScale(props: NumericScaleProps): ReactNode {
  const { range, value, onSelect, theme } = props;
  const options: number[] = [];
  for (let option = range.min; option <= range.max; option += 1) options.push(option);

  return (
    <View style={{ gap: theme.tokens.spacing.sm }}>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: theme.tokens.spacing.xs }}>
        {options.map((option) => (
          <PitacoScalePoint
            key={option}
            label={String(option)}
            selected={value === option}
            // Sempre seleciona: o core troca sozinho quando já havia um valor (e não emite nada
            // quando o toque repete o valor já escolhido).
            onPress={() => onSelect(option)}
          />
        ))}
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
  );
}
