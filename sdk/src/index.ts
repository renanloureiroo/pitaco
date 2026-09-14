// `@pitaco/react-native`

export { PitacoProvider, type PitacoProviderProps } from './react/PitacoProvider';
export { usePitaco, type PitacoActions } from './react/usePitaco';
export { PitacoBlock, type PitacoBlockProps } from './react/PitacoBlock';
export {
  usePitacoSurvey,
  type PitacoSurveyActions,
  type PitacoSurveyState,
  type UsePitacoSurveyResult,
} from './react/usePitacoSurvey';

export {
  CATALOG_VERSION,
  DISMISS_VIAS,
  INTERACTION_EVENT_DATA_FIELDS,
  INTERACTION_EVENT_TYPES,
  isInteractionEvent,
  isQuestionEventType,
  PRESENTATIONS,
  QUESTION_EVENT_TYPES,
  type AnswerEventValue,
  type CatalogVersion,
  type DismissVia,
  type InteractionEvent,
  type InteractionEventDataMap,
  type InteractionEventOf,
  type InteractionEventType,
  type PitacoEvent,
  type Presentation,
  type QuestionEventType,
  type QuestionLeftTo,
  type QuestionViewedFrom,
  type SurveyEventType,
} from './catalog/events';
export {
  isPlacementEvent,
  PLACEMENT_EVENT_TYPES,
  type PlacementEvent,
  type PlacementEventDataMap,
  type PlacementEventOf,
  type PlacementEventType,
  type SuppressionReason,
} from './catalog/placement';

export type { PitacoListenerEvent, RuntimeDiagnostics } from './core/runtime/runtime';
export type { QueueItem, QueueItemKind } from './core/queue/queue';
export type { Attributes, AttributeValue, Respondent } from './core/config';
export type { PitacoStorage } from './core/storage/types';
export { createMemoryStorage } from './core/storage/memory';
export type { SessionStatus, ValidationError } from './core/machine/machine';
export type { SurveyProgress, SurveySummary } from './core/session/snapshot';
export type { AnswerValue } from './core/survey/answers';
export type {
  ChoiceOption,
  ConditionOperator,
  FreeTextNotice,
  NumericRange,
  QuestionCondition,
  QuestionType,
  SurveyQuestion,
} from './core/survey/schema';
export { SDK_VERSION } from './version';

// UI padrão (fase 3): tema, textos, primitivos, contratos de customização e
// `<PitacoSurveyContent />`. Os renderizadores por tipo e as apresentações (bottom sheet, modal)
// entram nas fases seguintes, sobre estes mesmos contratos.
export {
  DEFAULT_DARK_THEME,
  DEFAULT_LIGHT_THEME,
  DEFAULT_STRINGS,
  hitSlopFor,
  MIN_TOUCH_TARGET,
  PitacoButton,
  PitacoChip,
  PitacoScalePoint,
  PitacoSurveyContent,
  PitacoText,
  rendererKeyForQuestionType,
  usePitacoStrings,
  usePitacoTheme,
  type ColorSchemeName,
  type ColorSchemePreference,
  type CloseButtonSlotProps,
  type EdgeInsets,
  type FooterSlotProps,
  type HeaderSlotProps,
  type PartialPitacoStrings,
  type PitacoButtonProps,
  type PitacoButtonVariant,
  type PitacoChipProps,
  type PitacoScalePointProps,
  type PitacoColorTokens,
  type PitacoRadiusTokens,
  type PitacoRendererKey,
  type PitacoRendererMap,
  type PitacoResolvedTheme,
  type PitacoQuestionRenderer,
  type PitacoSlotKey,
  type PitacoSlotMap,
  type PitacoSpacingTokens,
  type PitacoStrings,
  type PitacoSurveyContentProps,
  type PitacoSurveyFinishReason,
  type PitacoTextProps,
  type PitacoTextVariant,
  type PitacoThemeConfig,
  type PitacoThemeTokens,
  type PitacoThemeTokensOverride,
  type PitacoTypographyToken,
  type PitacoTypographyTokens,
  type ProgressSlotProps,
  type QuestionRendererActions,
  type QuestionRendererProps,
  type ThankYouSlotProps,
} from './ui';
export { PitacoPreview, type PitacoPreviewProps } from './preview/PitacoPreview';
