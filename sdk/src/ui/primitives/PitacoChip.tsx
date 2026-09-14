// Opção selecionável (escolha única ou múltipla), comum aos renderizadores provisórios de
// `SINGLE_CHOICE`, `MULTIPLE_CHOICE`, `RATING`, `SCALE` e `NPS`. Alvo de toque de ao menos 44 pt
// e papel de acessibilidade de rádio ou caixa de marcação, conforme `multiple`.

import { type ReactNode } from 'react';
import { Pressable, StyleSheet } from 'react-native';
import { usePitacoTheme } from '../theme/useTheme';
import { MIN_TOUCH_TARGET } from './PitacoButton';
import { PitacoText } from './PitacoText';

export interface PitacoChipProps {
  readonly label: string;
  readonly selected: boolean;
  readonly multiple?: boolean;
  readonly onPress: () => void;
  readonly accessibilityLabel?: string;
}

export function PitacoChip(props: PitacoChipProps): ReactNode {
  const { label, selected, multiple = false, onPress, accessibilityLabel } = props;
  const theme = usePitacoTheme();
  const { colors, radius, spacing } = theme.tokens;

  return (
    <Pressable
      accessibilityRole={multiple ? 'checkbox' : 'radio'}
      accessibilityState={{ checked: selected }}
      accessibilityLabel={accessibilityLabel ?? label}
      onPress={onPress}
      style={({ pressed }) => [
        styles.base,
        {
          minHeight: MIN_TOUCH_TARGET,
          borderRadius: radius.md,
          paddingHorizontal: spacing.md,
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
    justifyContent: 'center',
  },
});
