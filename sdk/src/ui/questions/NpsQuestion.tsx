// NPS: 0-10 (`NumericScale`, compartilhada com `SCALE`), com rótulos de ponta — os do schema
// quando vierem, senão o padrão de `strings` — e quebra em até duas linhas em tela estreita
// (11 pontos de 44 pt não cabem numa fileira só na maioria dos aparelhos; `flexWrap` resolve
// sozinho, tipicamente 6 + 5).

import type { ReactNode } from 'react';
import type { QuestionRendererProps } from '../types';
import { NPS_RANGE_FALLBACK } from './shared/nps';
import { NumericScale } from './shared/NumericScale';
import { QuestionErrorFrame } from './shared/QuestionErrorFrame';

export function NpsQuestion(props: QuestionRendererProps): ReactNode {
  const { question, value, error, actions, theme, strings } = props;
  const range = question.range ?? NPS_RANGE_FALLBACK;
  const withDefaultLabels = {
    ...range,
    minLabel: range.minLabel ?? strings.npsMinLabelDefault,
    maxLabel: range.maxLabel ?? strings.npsMaxLabelDefault,
  };

  return (
    <QuestionErrorFrame error={error} message={strings.validationRequired} theme={theme}>
      <NumericScale
        range={withDefaultLabels}
        value={typeof value === 'number' ? value : undefined}
        onSelect={actions.select}
        theme={theme}
      />
    </QuestionErrorFrame>
  );
}
