// Ponto numérico tocável, usado pelos renderizadores de `SCALE` e `NPS` (uma fileira de números
// que quebra linha em tela estreita). Quadrado, não uma pílula como `PitacoChip`: garante o
// alvo mínimo de 44 pt também na largura, o que um rótulo de um só dígito não garantiria sozinho.

import { type ReactNode } from 'react';
import { Pressable, StyleSheet } from 'react-native';
import { usePitacoTheme } from '../theme/useTheme';
import { MIN_TOUCH_TARGET } from './PitacoButton';
import { PitacoText } from './PitacoText';

export interface PitacoScalePointProps {
  readonly label: string;
  readonly selected: boolean;
  readonly onPress: () => void;
  readonly accessibilityLabel?: string;
}

export function PitacoScalePoint(props: PitacoScalePointProps): ReactNode {
  const { label, selected, onPress, accessibilityLabel } = props;
  const theme = usePitacoTheme();
  const { colors, radius } = theme.tokens;

  return (
    <Pressable
      accessibilityRole="radio"
      accessibilityState={{ checked: selected }}
      accessibilityLabel={accessibilityLabel ?? label}
      onPress={onPress}
      style={({ pressed }) => [
        styles.base,
        {
          minWidth: MIN_TOUCH_TARGET,
          minHeight: MIN_TOUCH_TARGET,
          borderRadius: radius.md,
          borderColor: selected ? colors.primary : colors.border,
          backgroundColor: selected ? colors.primary : colors.surface,
          opacity: pressed ? 0.8 : 1,
        },
      ]}
    >
      <PitacoText variant="body" color={selected ? colors.primaryText : colors.textPrimary}>
        {label}
      </PitacoText>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: {
    borderWidth: StyleSheet.hairlineWidth,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
