// Escolha única: lista de opções tocáveis, papel `radio` por opção. Tocar sempre seleciona — o
// core troca sozinho quando já havia um valor, e não emite nada quando o toque repete o valor já
// escolhido (`transition` em `core/machine/machine.ts`, caso `select` com `previous === value`).

import type { ReactNode } from 'react';
import { View } from 'react-native';
import { PitacoChip } from '../primitives/PitacoChip';
import type { QuestionRendererProps } from '../types';
import { QuestionErrorFrame } from './shared/QuestionErrorFrame';

export function SingleChoiceQuestion(props: QuestionRendererProps): ReactNode {
  const { question, value, error, actions, theme, strings } = props;

  return (
    <QuestionErrorFrame error={error} message={strings.validationRequired} theme={theme}>
      <View style={{ gap: theme.tokens.spacing.sm }}>
        {question.options.map((option) => {
          const selected = value === option.value;
          return (
            <PitacoChip
              key={option.value}
              label={option.label}
              selected={selected}
              onPress={() => actions.select(option.value)}
            />
          );
        })}
      </View>
    </QuestionErrorFrame>
  );
}
