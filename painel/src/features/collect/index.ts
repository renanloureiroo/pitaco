/**
 * Fronteira pública da feature de coleta (Princípio I).
 *
 * Só leitura atravessa daqui: quatro consultas, os tipos de domínio, os rótulos e os
 * componentes que `app/` compõe. Nenhuma Server Action — esta entrega não escreve nada.
 *
 * A dependência `collect → surveys` é unidirecional e passa pelo `index.ts` público de
 * `surveys`. A aba "Exibições" é composta em `app/`, não dentro de `surveys`: é o que evita o
 * ciclo (R1).
 */

export { listSurveyDisplays, getDisplay } from "./api/displays";
export { listRespondents, listRespondentDisplays } from "./api/respondents";

export {
  DISPLAY_OUTCOMES,
  type DisplayDetail,
  type DisplayOutcome,
  type DisplaySummary,
  type RespondentDisplay,
} from "./schemas/display";

export { ANSWER_STATUSES, type Answer, type AnswerStatus } from "./schemas/answer";

export {
  RESPONDENT_IDENTITY_KINDS,
  type Respondent,
  type RespondentIdentityKind,
} from "./schemas/respondent";

export {
  FILTER_PARAMS,
  FROM_PARAM,
  OUTCOME_PARAM,
  PERIOD_ERROR,
  TO_PARAM,
  VERSION_PARAM,
  hasAnyFilter,
  parseDisplayFilters,
  periodError,
  toDisplayFilterQuery,
  toLocalInput,
  toUtcInstant,
  type DisplayFilterQuery,
  type DisplayFilters,
} from "./schemas/filters";

export {
  ANSWER_BLANK,
  ANSWER_EXPIRED_EXPLANATION,
  ANSWER_SKIPPED_EXPLANATION,
  ANSWER_STATUS_LABELS,
  DISPLAY_OUTCOME_LABELS,
  DISPLAY_STILL_OPEN,
  QUESTION_NOT_IN_VERSION,
  RESPONDENT_IDENTITY_KIND_LABELS,
  SDK_VERSION_UNKNOWN,
  displayOutcomeVariant,
} from "./lib/collect-labels";

export {
  displaysEmptyVariant,
  historyEmptyVariant,
  type DisplaysEmptyVariant,
  type HistoryEmptyVariant,
} from "./lib/empty-variants";

export { DisplaysTable } from "./components/displays/displays-table";
export { DisplayFiltersForm } from "./components/displays/display-filters";
export { DisplaySummaryCard } from "./components/displays/display-summary";
export { DisplayAnswers } from "./components/displays/display-answers";
export { DisplayAttributes } from "./components/displays/display-attributes";

export { RespondentsTable } from "./components/respondents/respondents-table";
export { RespondentSummary } from "./components/respondents/respondent-summary";

export { answerValueText, matchAnswersToQuestions, type ResolvedAnswer } from "./lib/answers";
