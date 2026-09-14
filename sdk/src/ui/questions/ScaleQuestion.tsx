// Escala: pontos numéricos tocáveis (`NumericScale`, compartilhada com `NPS`) com os rótulos de
// escala do schema nas pontas, e quebra de linha sozinha em tela estreita. `value` é sempre o
// número escolhido, nunca o rótulo.

import type { ReactNode } from 'react';
import type { QuestionRendererProps } from '../types';
import { NumericScale } from './shared/NumericScale';
import { QuestionErrorFrame } from './shared/QuestionErrorFrame';

export function ScaleQuestion(props: QuestionRendererProps): ReactNode {
  const { question, value, error, actions, theme, strings } = props;
  if (question.range === null) return null;

  return (
    <QuestionErrorFrame error={error} message={strings.validationRequired} theme={theme}>
      <NumericScale
        range={question.range}
        value={typeof value === 'number' ? value : undefined}
        onSelect={actions.select}
        theme={theme}
      />
    </QuestionErrorFrame>
  );
}
