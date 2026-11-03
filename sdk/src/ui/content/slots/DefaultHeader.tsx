import type { ReactNode } from 'react';
import { View } from 'react-native';
import { PitacoText } from '../../primitives/PitacoText';
import type { HeaderSlotProps } from '../../types';

// Cabeçalho padrão: só o progresso textual ("Pergunta X de Y"). A barra visual é o slot
// `Progress`, mostrado logo abaixo dele por `<PitacoSurveyContent />`.
export function DefaultHeader(props: HeaderSlotProps): ReactNode {
  const { progress, theme, strings } = props;
  return (
    <View style={{ paddingHorizontal: theme.tokens.spacing.lg, paddingTop: theme.tokens.spacing.md }}>
      <PitacoText variant="caption" color={theme.tokens.colors.textSecondary}>
        {strings.progress({ position: progress.position, total: progress.total })}
      </PitacoText>
    </View>
  );
}
