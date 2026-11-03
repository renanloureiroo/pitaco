/**
 * Fronteira pública da feature de pesquisas (Princípio I).
 *
 * Perguntas, disparo, versões, publicação e ciclo de vida são submódulos internos, não
 * features próprias: compartilham o mesmo agregado, o mesmo identificador e o mesmo estado
 * (R15). Separá-los criaria justamente o import cruzado que o Princípio I proíbe.
 */

export { listSurveys, getSurvey, getQuotaProgress } from "./api/surveys";
export { getPublicationImpediments, getPublicationWarnings } from "./api/publication";
export { getTransitions } from "./api/lifecycle";
export {
  listVersions,
  getVersion,
  getVersionComparability,
} from "./api/versions";

export {
  createSurveyAction,
  duplicateSurveyAction,
  renameSurveyAction,
  updateExposureAction,
  updateFreeTextNoticeAction,
  discardSurveyAction,
  addQuestionAction,
  updateQuestionAction,
  removeQuestionAction,
  moveQuestionAction,
  reorderQuestionsAction,
  defineTriggerAction,
  addSegmentationRuleAction,
  removeSegmentationRuleAction,
  publishSurveyAction,
  openDraftVersionAction,
  discardDraftVersionAction,
  pauseSurveyAction,
  resumeSurveyAction,
  endSurveyAction,
} from "./actions";

export {
  SURVEY_STATES,
  SURVEY_TEMPLATES,
  isAssemblyReadOnly,
  type FreeTextNotice,
  type SurveyTemplate,
  type Survey,
  type SurveyContent,
  type SurveyDetail,
  type SurveyState,
} from "./schemas/survey";

export {
  TRANSITION_REASONS,
  TRANSITION_REASON_LABELS,
  allowedManualReasons,
  type SurveyStateTransition,
} from "./schemas/transition";

export {
  PRIORITY_MAX,
  PRIORITY_MIN,
  type ExposureForm,
  type QuotaProgress,
} from "./schemas/exposure";

export {
  WARNING_CODES,
  type CompetingSurvey,
  type PublicationWarning,
} from "./schemas/warnings";

export { TIEBREAK_RULE, warningMessage } from "./lib/warning-messages";

export {
  CHANGE_KINDS,
  CHANGE_KIND_LABELS,
  IMPEDIMENT_CODES,
  type PublicationImpediment,
} from "./schemas/publication";

export {
  type SurveyVersion,
  type SurveyVersionDetail,
  type VersionComparability,
} from "./schemas/version";

export {
  RULE_OPERATIONS,
  requiresValue,
  type SegmentationRule,
  type Trigger,
} from "./schemas/trigger";

export {
  QUESTION_TYPES,
  acceptsRange,
  requiresOptions,
  type Question,
  type QuestionOption,
  type QuestionType,
} from "./schemas/question";

export {
  QUESTION_TYPE_LABELS,
  RULE_OPERATION_LABELS,
  SURVEY_STATE_LABELS,
  surveyStateVariant,
} from "./lib/survey-labels";

export { SurveysTable } from "./components/survey/surveys-table";
export { SurveyHeader } from "./components/survey/survey-header";
export { DuplicateSurveyDialog } from "./components/survey/duplicate-survey-dialog";
export { SURVEY_TEMPLATE_LABELS, SURVEY_TEMPLATE_OPTIONS } from "./lib/survey-templates";
export { describeCondition } from "./lib/condition";
export { CONDITION_OPERATORS, type Condition, type ConditionOperator } from "./schemas/condition";
export { SurveyForm } from "./components/survey/survey-form";
export { RenameSurvey } from "./components/survey/rename-survey";
export { DiscardSurveyButton } from "./components/survey/discard-survey-button";
export { QuestionsPanel } from "./components/questions/questions-panel";
export { TriggerPanel } from "./components/trigger/trigger-panel";
export { ExposurePanel } from "./components/trigger/exposure-panel";
export { FreeTextNoticePanel } from "./components/trigger/free-text-notice-panel";
export { WarningsList } from "./components/publication/warnings-list";
export type { ObservedAttributeSuggestion } from "./components/trigger/rule-form";
export { ImpedimentsList } from "./components/publication/impediments-list";
export { PublishForm } from "./components/publication/publish-form";
export { VersionsTable } from "./components/versions/versions-table";
export { ComparabilityPanel } from "./components/versions/comparability-panel";
export { VersionDetail } from "./components/versions/version-detail";
export { DraftVersionActions } from "./components/versions/draft-version-actions";
export { TransitionActions } from "./components/lifecycle/transition-actions";
export { TransitionsHistory } from "./components/lifecycle/transitions-history";
