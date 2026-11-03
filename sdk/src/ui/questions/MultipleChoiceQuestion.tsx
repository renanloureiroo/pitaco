// Múltipla escolha: tocar adiciona (`select`) ou remove (`deselect`) a opção. Papel `checkbox`
// por opção. O schema atual (`core/survey/schema.ts`) não traz mínimo/máximo de seleções para
// este tipo — só o teto natural do número de opções — então não há limite a validar aqui; se um
// contrato futuro trouxer `minSelections`/`maxSelections`, a validação continua sendo do core
// (evento `validation_blocked`), e este renderizador só precisaria refletir o estado.

import type { ReactNode } from 'react';
import { View } from 'react-native';
import { PitacoChip } from '../primitives/PitacoChip';
import { PitacoText } from '../primitives/PitacoText';
import type { QuestionRendererProps } from '../types';
import { QuestionErrorFrame } from './shared/QuestionErrorFrame';

export function MultipleChoiceQuestion(props: QuestionRendererProps): ReactNode {
  const { question, value, error, actions, theme, strings } = props;
  const selectedValues: readonly string[] = Array.isArray(value) ? value : [];

  return (
    <QuestionErrorFrame error={error} message={strings.validationRequired} theme={theme}>
      <View style={{ gap: theme.tokens.spacing.sm }}>
        <PitacoText variant="caption" color={theme.tokens.colors.textSecondary}>
          {strings.multipleChoiceHint}
        </PitacoText>
        {question.options.map((option) => {
          const selected = selectedValues.includes(option.value);
          return (
            <PitacoChip
              key={option.value}
              label={option.label}
              selected={selected}
              multiple
              onPress={() => (selected ? actions.deselect(option.value) : actions.select(option.value))}
            />
          );
        })}
      </View>
    </QuestionErrorFrame>
  );
}
