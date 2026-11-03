import type { ReactNode } from 'react';
import { View } from 'react-native';
import type { ProgressSlotProps } from '../../types';

// Barra de progresso padrão: preenchimento proporcional a `position / total`. `total` é uma
// estimativa (pode diminuir quando uma condição pula perguntas) e nunca deixa a barra passar
// de 100%.
export function DefaultProgress(props: ProgressSlotProps): ReactNode {
  const { position, total, theme, strings } = props;
  const ratio = total > 0 ? Math.min(1, position / total) : 0;

  return (
    <View
      accessible
      accessibilityRole="progressbar"
      accessibilityLabel={strings.progress({ position, total })}
      accessibilityValue={{ min: 0, max: total, now: position }}
      style={{
        marginHorizontal: theme.tokens.spacing.lg,
        marginTop: theme.tokens.spacing.xs,
        height: 4,
        borderRadius: theme.tokens.radius.pill,
        backgroundColor: theme.tokens.colors.border,
        overflow: 'hidden',
      }}
    >
      <View
        style={{
          width: `${ratio * 100}%`,
          height: '100%',
          backgroundColor: theme.tokens.colors.primary,
        }}
      />
    </View>
  );
}
