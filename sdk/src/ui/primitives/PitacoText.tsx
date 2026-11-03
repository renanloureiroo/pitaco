// Texto padrão da UI do Pitaco: respeita a escala de fonte do sistema (`allowFontScaling`,
// ligado por padrão no React Native), com um teto sensato (`maxFontSizeMultiplier`) para não
// quebrar o layout em telas pequenas quando a pessoa aumenta muito a fonte do aparelho.

import type { ReactNode } from 'react';
import { Text, type TextProps } from 'react-native';
import { usePitacoTheme } from '../theme/useTheme';
import type { PitacoTypographyToken } from '../theme/tokens';

export type PitacoTextVariant = 'title' | 'body' | 'label' | 'caption' | 'button';

export interface PitacoTextProps extends TextProps {
  readonly variant?: PitacoTextVariant;
  // Sobrepõe a cor do token de tipografia; por padrão usa `colors.textPrimary`.
  readonly color?: string;
}

// Generoso o bastante para acompanhar quem aumenta a fonte do sistema, sem deixar um título de
// uma palavra virar três linhas de altura descontrolada. Exportado para quem precisa do mesmo
// teto num `Text` cru fora deste primitivo — hoje só o título da pergunta em
// `<PitacoSurveyContent />`, que precisa de uma ref de verdade (o foco de acessibilidade) e por
// isso não passa por aqui.
export const DEFAULT_MAX_FONT_SIZE_MULTIPLIER = 1.6;

function tokenStyle(token: PitacoTypographyToken, color: string) {
  return { fontSize: token.fontSize, lineHeight: token.lineHeight, fontWeight: token.fontWeight, color };
}

export function PitacoText(props: PitacoTextProps): ReactNode {
  const { variant = 'body', color, style, maxFontSizeMultiplier, ...rest } = props;
  const theme = usePitacoTheme();
  const token = theme.tokens.typography[variant];
  const resolvedColor = color ?? theme.tokens.colors.textPrimary;

  return (
    <Text
      maxFontSizeMultiplier={maxFontSizeMultiplier ?? DEFAULT_MAX_FONT_SIZE_MULTIPLIER}
      style={[tokenStyle(token, resolvedColor), style]}
      {...rest}
    />
  );
}
