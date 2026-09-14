// Suporte só dos testes dos seis renderizadores: monta `QuestionRendererProps` isoladamente
// (tema já resolvido, strings padrão, ações espiãs), sem precisar do Provider nem do core — os
// renderizadores só usam o que chega por props (contrato da fase 3a).
//
// Os primitivos (`PitacoChip`, `PitacoScalePoint`, `PitacoText`) resolvem o próprio tema via
// `usePitacoTheme()` (o contexto), não a partir do `theme` recebido por prop pelo renderizador —
// é assim que funcionam dentro do `<PitacoProvider>` de verdade, onde os dois sempre coincidem
// (mesma fonte). Para testar o esquema escuro isoladamente, os testes precisam envolver o
// renderizador em `PitacoContext.Provider` com o mesmo esquema do `theme` que passam por prop:
// `renderQuestion(<X {...props} />, 'dark')` faz isso.

import { render, type RenderResult } from '@testing-library/react-native';
import type { ReactElement } from 'react';
import { PitacoContext, type PitacoContextValue } from '../../../react/context';
import { DEFAULT_STRINGS } from '../../strings/strings';
import { resolvePitacoTheme } from '../../theme/useTheme';
import type { ColorSchemeName } from '../../theme/tokens';
import type { PitacoResolvedTheme } from '../../theme/tokens';
import type { ValidationError } from '../../../core/machine/machine';
import type { AnswerValue } from '../../../core/survey/answers';
import type { SurveyQuestion } from '../../../core/survey/schema';
import type { QuestionRendererActions, QuestionRendererProps } from '../../types';

export function themeFor(scheme: ColorSchemeName = 'light'): PitacoResolvedTheme {
  return resolvePitacoTheme({ colorScheme: scheme }, scheme);
}

function contextFor(scheme: ColorSchemeName): PitacoContextValue {
  return {
    runtime: null,
    controller: null,
    reportRenderError: () => undefined,
    ui: { presentation: 'bottom-sheet', theme: { colorScheme: scheme } },
  };
}

// Renderiza dentro de um `PitacoContext.Provider` com o mesmo esquema de cor do `theme` passado
// por prop ao renderizador, para que os primitivos (que leem o próprio tema do contexto) mostrem
// as mesmas cores que o teste está checando via `props.theme`.
export function renderQuestion(node: ReactElement, scheme: ColorSchemeName = 'light'): Promise<RenderResult> {
  return render(<PitacoContext.Provider value={contextFor(scheme)}>{node}</PitacoContext.Provider>);
}

export function spyActions(): QuestionRendererActions {
  return {
    select: jest.fn(),
    deselect: jest.fn(),
    setText: jest.fn(),
    focusText: jest.fn(),
    blurText: jest.fn(),
  };
}

export function requiredMissingError(question: SurveyQuestion): ValidationError {
  return { questionKey: question.key, reason: 'required_missing' };
}

export function rendererProps(
  question: SurveyQuestion,
  overrides: Partial<QuestionRendererProps> = {},
): QuestionRendererProps {
  return {
    question,
    value: undefined as AnswerValue | undefined,
    error: null,
    actions: spyActions(),
    theme: themeFor('light'),
    strings: DEFAULT_STRINGS,
    freeTextNotice: null,
    ...overrides,
  };
}
