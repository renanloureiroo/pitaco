/**
 * Fronteira pública da feature de pesquisas (Princípio I).
 *
 * Perguntas, disparo, versões, publicação e ciclo de vida são submódulos internos, não
 * features próprias: compartilham o mesmo agregado, o mesmo identificador e o mesmo estado
 * (R15). Separá-los criaria justamente o import cruzado que o Princípio I proíbe.
 */

export { listSurveys, getSurvey } from "./api/surveys";
export { getPublicationImpediments } from "./api/publication";
export { getTransitions } from "./api/lifecycle";
export {
  listVersions,
  getVersion,
  getVersionComparability,
} from "./api/versions";

export {
  createSurveyAction,
  renameSurveyAction,
  discardSurveyAction,
  addQuestionAction,
  updateQuestionAction,
  removeQuestionAction,
  moveQuestionAction,
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
  isAssemblyReadOnly,
  type Survey,
  type SurveyContent,
  type SurveyDetail,
  type SurveyState,
} from "./schemas/survey";

export {
  TRANSITION_REASONS,
  TRANSITION_REASON_LABELS,
  allowedManualReasons,
  registeredTransitions,
  type SurveyStateTransition,
} from "./schemas/transition";

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
export { SurveyForm } from "./components/survey/survey-form";
export { RenameSurvey } from "./components/survey/rename-survey";
export { DiscardSurveyButton } from "./components/survey/discard-survey-button";
export { QuestionsPanel } from "./components/questions/questions-panel";
export { TriggerPanel } from "./components/trigger/trigger-panel";
export { ImpedimentsList } from "./components/publication/impediments-list";
export { PublishForm } from "./components/publication/publish-form";
export { VersionsTable } from "./components/versions/versions-table";
export { ComparabilityPanel } from "./components/versions/comparability-panel";
export { VersionDetail } from "./components/versions/version-detail";
export { DraftVersionActions } from "./components/versions/draft-version-actions";
export { TransitionActions } from "./components/lifecycle/transition-actions";
export { TransitionsHistory } from "./components/lifecycle/transitions-history";
