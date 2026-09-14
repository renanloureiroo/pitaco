import type { ReactNode } from 'react';
import { Pressable, StyleSheet } from 'react-native';
import { MIN_TOUCH_TARGET } from '../../primitives/PitacoButton';
import { PitacoText } from '../../primitives/PitacoText';
import type { CloseButtonSlotProps } from '../../types';

// Botão de fechar padrão, presente em toda pergunta: um "×" com alvo mínimo de 44 pt via
// `hitSlop`, já que o visual (a marca) é bem menor.
export function DefaultCloseButton(props: CloseButtonSlotProps): ReactNode {
  const { onClose, theme, strings } = props;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={strings.closeA11yLabel}
      onPress={onClose}
      hitSlop={16}
      style={styles.touchArea}
    >
      <PitacoText variant="title" color={theme.tokens.colors.textSecondary}>
        ×
      </PitacoText>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  touchArea: {
    minWidth: MIN_TOUCH_TARGET / 2,
    minHeight: MIN_TOUCH_TARGET / 2,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
