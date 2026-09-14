// Moldura de erro de validação, compartilhada pelos seis renderizadores: destaca a área
// interativa com uma borda de perigo e anuncia a mensagem ao leitor de tela na transição de
// "sem erro" para "com erro" (`AccessibilityInfo.announceForAccessibility`), além de marcar a
// região como `accessibilityLiveRegion` para o TalkBack acompanhar sem precisar de foco.
//
// A mensagem de validação em si (`strings.validationRequired`) já é mostrada por
// `<PitacoSurveyContent />` acima do renderizador (contrato da fase 3a); esta moldura nunca a
// repete, só destaca visualmente e para leitor de tela a opção/campo que falhou.

import { useEffect, useRef, type ReactNode } from 'react';
import { AccessibilityInfo, View } from 'react-native';
import type { ValidationError } from '../../../core/machine/machine';
import type { PitacoResolvedTheme } from '../../theme/tokens';

export interface QuestionErrorFrameProps {
  readonly error: ValidationError | null;
  // Anunciada ao leitor de tela quando o erro aparece; não é renderizada como texto aqui.
  readonly message: string;
  readonly theme: PitacoResolvedTheme;
  readonly children: ReactNode;
}

export function QuestionErrorFrame(props: QuestionErrorFrameProps): ReactNode {
  const { error, message, theme, children } = props;
  const hasError = error !== null;
  const hadErrorRef = useRef(false);

  useEffect(() => {
    if (hasError && !hadErrorRef.current) {
      AccessibilityInfo.announceForAccessibility(message);
    }
    hadErrorRef.current = hasError;
  }, [hasError, message]);

  return (
    <View
      accessibilityLiveRegion={hasError ? 'polite' : 'none'}
      style={
        hasError
          ? {
              borderWidth: 1,
              borderColor: theme.tokens.colors.danger,
              borderRadius: theme.tokens.radius.md,
              padding: theme.tokens.spacing.xs,
            }
          : undefined
      }
    >
      {children}
    </View>
  );
}
