// Botão padrão da UI do Pitaco: alvo de toque de ao menos 44 pt (com `hitSlop` quando o visual é
// menor), papel e rótulo de acessibilidade, e estados desabilitado e pressionado.

import { type ReactNode, useMemo, useState } from 'react';
import { Pressable, StyleSheet, View, type PressableProps } from 'react-native';
import { usePitacoTheme } from '../theme/useTheme';
import { PitacoText, type PitacoTextVariant } from './PitacoText';

export const MIN_TOUCH_TARGET = 44;

export type PitacoButtonVariant = 'primary' | 'secondary' | 'text';

export interface PitacoButtonProps extends Omit<PressableProps, 'style' | 'children'> {
  readonly label: string;
  // Rótulo de acessibilidade, quando diferente do texto visível (ex.: um botão só com ícone).
  readonly accessibilityLabel?: string;
  readonly variant?: PitacoButtonVariant;
  readonly disabled?: boolean;
  // Elemento à esquerda do rótulo (um ícone, por exemplo); não altera o alvo mínimo de toque.
  readonly icon?: ReactNode;
}

function textVariantFor(variant: PitacoButtonVariant): PitacoTextVariant {
  return variant === 'text' ? 'label' : 'button';
}

// Calcula o `hitSlop` necessário para o alvo total de toque chegar a 44 pt, a partir da altura
// visual medida do próprio botão.
export function hitSlopFor(measuredHeight: number, minTarget: number = MIN_TOUCH_TARGET): number {
  const deficit = minTarget - measuredHeight;
  return deficit > 0 ? Math.ceil(deficit / 2) : 0;
}

export function PitacoButton(props: PitacoButtonProps): ReactNode {
  const { label, accessibilityLabel, variant = 'primary', disabled = false, icon, onLayout, hitSlop, ...rest } = props;
  const theme = usePitacoTheme();
  const { colors, radius, spacing } = theme.tokens;

  const [measuredHeight, setMeasuredHeight] = useState(MIN_TOUCH_TARGET);
  const resolvedHitSlop = hitSlop ?? hitSlopFor(measuredHeight);

  const palette = useMemo(() => {
    if (disabled) return { background: colors.disabled, text: colors.disabledText, border: colors.disabled };
    switch (variant) {
      case 'primary':
        return { background: colors.primary, text: colors.primaryText, border: colors.primary };
      case 'secondary':
        return { background: colors.surface, text: colors.textPrimary, border: colors.border };
      case 'text':
        return { background: 'transparent', text: colors.primary, border: 'transparent' };
    }
  }, [colors, disabled, variant]);

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel ?? label}
      accessibilityState={{ disabled }}
      disabled={disabled}
      hitSlop={resolvedHitSlop}
      onLayout={(event) => {
        setMeasuredHeight(event.nativeEvent.layout.height);
        onLayout?.(event);
      }}
      style={({ pressed }) => [
        styles.base,
        {
          backgroundColor: palette.background,
          borderColor: palette.border,
          borderRadius: radius.md,
          paddingHorizontal: spacing.lg,
          paddingVertical: spacing.sm,
          minHeight: MIN_TOUCH_TARGET,
          opacity: pressed && !disabled ? 0.72 : 1,
        },
      ]}
      {...rest}
    >
      <View style={styles.content}>
        {icon}
        <PitacoText variant={textVariantFor(variant)} color={palette.text}>
          {label}
        </PitacoText>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: {
    borderWidth: StyleSheet.hairlineWidth,
    alignItems: 'center',
    justifyContent: 'center',
  },
  content: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
});
