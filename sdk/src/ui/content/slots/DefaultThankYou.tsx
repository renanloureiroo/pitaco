import { type ReactNode, useEffect } from 'react';
import { AccessibilityInfo, View } from 'react-native';
import { PitacoText } from '../../primitives/PitacoText';
import type { ThankYouSlotProps } from '../../types';

// Agradecimento padrão: curto, some sozinho depois de `durationMs` (quem chama `<PitacoSurveyContent
// onFinish>` decide o que fazer quando `onDone` disparar — normalmente fechar o próprio contêiner).
export function DefaultThankYou(props: ThankYouSlotProps): ReactNode {
  const { theme, strings, durationMs, onDone } = props;

  useEffect(() => {
    const timer = setTimeout(onDone, durationMs);
    return () => clearTimeout(timer);
  }, [durationMs, onDone]);

  // `accessibilityRole="alert"` sozinho não garante o anúncio em todo leitor de tela quando o
  // elemento já nasce montado (sem transição de invisível→visível para o SO notar); o anúncio
  // explícito garante que quem usa VoiceOver/TalkBack ouça o agradecimento mesmo assim.
  useEffect(() => {
    AccessibilityInfo.announceForAccessibility(`${strings.thankYouTitle}. ${strings.thankYouBody}`);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <View
      accessible
      accessibilityRole="alert"
      accessibilityLiveRegion="polite"
      style={{
        padding: theme.tokens.spacing.xl,
        alignItems: 'center',
        gap: theme.tokens.spacing.sm,
      }}
    >
      <PitacoText variant="title">{strings.thankYouTitle}</PitacoText>
      <PitacoText variant="body" color={theme.tokens.colors.textSecondary}>
        {strings.thankYouBody}
      </PitacoText>
    </View>
  );
}
