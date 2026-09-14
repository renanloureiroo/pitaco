// Contratos públicos de customização da UI padrão: o que um renderizador de pergunta e um slot
// de moldura recebem, e a forma de `renderers`/`slots` no Provider e em `<PitacoSurveyContent />`.
//
// Estes tipos são a fronteira entre a fase 3a (esta) e as fases 3b (os seis renderizadores, em
// `src/ui/questions/`) e 3c (as apresentações, em `src/ui/presentation/` e
// `src/react/SurfaceHost.tsx`): mudar a forma aqui é mudança de contrato entre as três.

import type { ReactNode } from 'react';
import type { Presentation } from '../catalog/events';
import type { ValidationError } from '../core/machine/machine';
import type { AnswerValue } from '../core/survey/answers';
import type { SurveyProgress, SurveySummary } from '../core/session/snapshot';
import type { QuestionType, SurveyQuestion } from '../core/survey/schema';
import type { PitacoResolvedTheme, PitacoThemeConfig } from './theme/tokens';
import type { PartialPitacoStrings, PitacoStrings } from './strings/strings';

// --- Renderização por tipo de pergunta -------------------------------------------------------

// As ações do core já vinculadas à pergunta atual: chamar `select`/`deselect`/`setText` aqui
// nunca precisa (nem aceita) `questionKey` — quem decide qual é a pergunta atual é o core.
export interface QuestionRendererActions {
  readonly select: (value: string | number) => void;
  readonly deselect: (value?: string | number) => void;
  readonly setText: (text: string) => void;
  readonly focusText: () => void;
  readonly blurText: () => void;
}

export interface QuestionRendererProps<T extends SurveyQuestion = SurveyQuestion> {
  readonly question: T;
  readonly value: AnswerValue | undefined;
  readonly error: ValidationError | null;
  readonly actions: QuestionRendererActions;
  readonly theme: PitacoResolvedTheme;
  readonly strings: PitacoStrings;
  // Aviso de texto livre da versão publicada (feature 8), pronto para o renderizador de
  // `FREE_TEXT` mostrar; os outros tipos o ignoram.
  readonly freeTextNotice: string | null;
}

export type PitacoQuestionRenderer<T extends SurveyQuestion = SurveyQuestion> = (
  props: QuestionRendererProps<T>,
) => ReactNode;

// Chave por tipo de pergunta, em minúsculo-camelo a partir do catálogo (`QUESTION_TYPES`).
// `renderers={{ nps: MeuNps }}` no Provider ou em `<PitacoSurveyContent />` usa estas chaves.
export interface PitacoRendererMap {
  readonly singleChoice: PitacoQuestionRenderer;
  readonly multipleChoice: PitacoQuestionRenderer;
  readonly rating: PitacoQuestionRenderer;
  readonly scale: PitacoQuestionRenderer;
  readonly nps: PitacoQuestionRenderer;
  readonly freeText: PitacoQuestionRenderer;
}

export type PitacoRendererKey = keyof PitacoRendererMap;

const RENDERER_KEY_BY_QUESTION_TYPE: Readonly<Record<QuestionType, PitacoRendererKey>> = {
  SINGLE_CHOICE: 'singleChoice',
  MULTIPLE_CHOICE: 'multipleChoice',
  RATING: 'rating',
  SCALE: 'scale',
  NPS: 'nps',
  FREE_TEXT: 'freeText',
};

export function rendererKeyForQuestionType(type: QuestionType): PitacoRendererKey {
  return RENDERER_KEY_BY_QUESTION_TYPE[type];
}

// --- Slots de moldura --------------------------------------------------------------------------

export interface HeaderSlotProps {
  readonly survey: SurveySummary;
  readonly progress: SurveyProgress;
  readonly theme: PitacoResolvedTheme;
  readonly strings: PitacoStrings;
}

export interface ProgressSlotProps {
  readonly position: number;
  readonly total: number;
  readonly theme: PitacoResolvedTheme;
  readonly strings: PitacoStrings;
}

export interface FooterSlotProps {
  readonly canGoBack: boolean;
  readonly canGoNext: boolean;
  readonly isLast: boolean;
  readonly onBack: () => void;
  // Avança; na última pergunta aplicável o core conclui sozinho (o rótulo já muda para "Enviar").
  readonly onNext: () => void;
  readonly theme: PitacoResolvedTheme;
  readonly strings: PitacoStrings;
}

export interface CloseButtonSlotProps {
  readonly onClose: () => void;
  readonly theme: PitacoResolvedTheme;
  readonly strings: PitacoStrings;
}

export interface ThankYouSlotProps {
  readonly theme: PitacoResolvedTheme;
  readonly strings: PitacoStrings;
  // Quanto tempo o slot tem para desaparecer sozinho; `<PitacoSurveyContent />` chama `onDone`
  // quando esse tempo se esgota (o slot substituído pode chamar antes, se quiser).
  readonly durationMs: number;
  readonly onDone: () => void;
}

export interface PitacoSlotMap {
  readonly Header: (props: HeaderSlotProps) => ReactNode;
  readonly Progress: (props: ProgressSlotProps) => ReactNode;
  readonly Footer: (props: FooterSlotProps) => ReactNode;
  readonly CloseButton: (props: CloseButtonSlotProps) => ReactNode;
  readonly ThankYou: (props: ThankYouSlotProps) => ReactNode;
}

export type PitacoSlotKey = keyof PitacoSlotMap;

// --- Safe area sem dependência -------------------------------------------------------------

// A mesma forma de `useSafeAreaInsets()` do `react-native-safe-area-context`, para quem já usa o
// pacote passar o próprio hook direto em `getInsets`. O SDK nunca importa esse pacote (restrição
// 1); é o app que decide se tem uma fonte melhor que o padrão conservador por plataforma.
export interface EdgeInsets {
  readonly top: number;
  readonly right: number;
  readonly bottom: number;
  readonly left: number;
}

// --- Configuração de UI exposta pelo Provider ao contexto --------------------------------------

// O que o Provider entrega ao contexto para a UI padrão e para as apresentações da fase 3c
// lerem. `theme`/`strings` são a configuração ainda não resolvida (a resolução, com o esquema de
// cor do sistema e a validação, é o que `usePitacoTheme`/`usePitacoStrings` fazem); `renderers`/
// `slots` são as substituições do Provider (uma prop local em `<PitacoSurveyContent />`
// sobrepõe estas). `presentation` e os insets são só para a fase 3c: esta fase não os usa.
export interface PitacoUiConfig {
  readonly theme?: PitacoThemeConfig;
  readonly strings?: PartialPitacoStrings;
  readonly renderers?: Partial<PitacoRendererMap>;
  readonly slots?: Partial<PitacoSlotMap>;
  readonly presentation: Presentation;
  readonly insets?: EdgeInsets;
  readonly getInsets?: () => EdgeInsets;
}
