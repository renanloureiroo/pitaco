// Texto livre: campo multilinha ligado a `setText`/`focusText`/`blurText` do core, contador de
// caracteres contra o teto do core (`MAX_FREE_TEXT_LENGTH`, `core/survey/answers.ts` — o mesmo
// que `setText` já aplica, então o campo nunca deixa passar do que o core aceitaria) e o aviso de
// texto livre (`freeTextNotice`, já resolvido por `<PitacoSurveyContent />`: o texto configurado
// na versão publicada, ou o padrão de `strings` quando o recurso está ligado sem texto próprio),
// exibido junto do campo e anunciado ao leitor de tela quando aparece.

import { useEffect, useRef, type ReactNode } from 'react';
import { AccessibilityInfo, TextInput, View } from 'react-native';
import { MAX_FREE_TEXT_LENGTH, textLength } from '../../core/survey/answers';
import { DEFAULT_MAX_FONT_SIZE_MULTIPLIER, PitacoText } from '../primitives/PitacoText';
import type { QuestionRendererProps } from '../types';
import { QuestionErrorFrame } from './shared/QuestionErrorFrame';

export function FreeTextQuestion(props: QuestionRendererProps): ReactNode {
  const { value, error, actions, theme, strings, freeTextNotice } = props;
  const { colors, radius, spacing, typography } = theme.tokens;
  const text = typeof value === 'string' ? value : '';
  const length = textLength(value);

  // Anuncia o aviso de texto livre uma vez, quando ele existe — o texto continua visível junto
  // do campo o tempo todo, para quem não usa leitor de tela.
  const announcedRef = useRef(false);
  useEffect(() => {
    if (freeTextNotice !== null && !announcedRef.current) {
      AccessibilityInfo.announceForAccessibility(freeTextNotice);
      announcedRef.current = true;
    }
  }, [freeTextNotice]);

  return (
    <QuestionErrorFrame error={error} message={strings.validationRequired} theme={theme}>
      <View style={{ gap: spacing.xs }}>
        <TextInput
          value={text}
          onChangeText={actions.setText}
          onFocus={actions.focusText}
          onBlur={actions.blurText}
          placeholder={strings.freeTextPlaceholder}
          placeholderTextColor={colors.textSecondary}
          multiline
          maxLength={MAX_FREE_TEXT_LENGTH}
          maxFontSizeMultiplier={DEFAULT_MAX_FONT_SIZE_MULTIPLIER}
          accessibilityLabel={strings.freeTextPlaceholder}
          style={{
            minHeight: 96,
            borderWidth: 1,
            borderColor: colors.border,
            borderRadius: radius.md,
            padding: spacing.md,
            color: colors.textPrimary,
            fontSize: typography.body.fontSize,
            textAlignVertical: 'top',
          }}
        />
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', gap: spacing.sm }}>
          {freeTextNotice !== null ? (
            <PitacoText variant="caption" color={colors.textSecondary} style={{ flex: 1 }}>
              {freeTextNotice}
            </PitacoText>
          ) : (
            <View style={{ flex: 1 }} />
          )}
          <PitacoText
            variant="caption"
            color={colors.textSecondary}
            accessibilityLabel={`${length} de ${MAX_FREE_TEXT_LENGTH} caracteres`}
          >
            {`${length}/${MAX_FREE_TEXT_LENGTH}`}
          </PitacoText>
        </View>
      </View>
    </QuestionErrorFrame>
  );
}
