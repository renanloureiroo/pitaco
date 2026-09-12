/** Fronteira pública da feature de resultados (Princípio I). */

export { getSurveyResults, listOpenAnswers, backendExportUrl, exportHref } from "./api/results";

export {
  ABSENT_PARAM,
  ATTRIBUTE_PARAM,
  FILTER_PARAMS,
  FROM_PARAM,
  PERIOD_LABELS,
  PERIOD_PARAM,
  PERIOD_SHORTCUTS,
  TERM_PARAM,
  TO_PARAM,
  VALUE_PARAM,
  VERSION_PARAM,
  describeFilters,
  hasAnyFilter,
  parseResultsFilters,
  parseTerm,
  periodError,
  toResultsQuery,
  type ResultsFilters,
  type ResultsQuery,
} from "./schemas/filters";

export {
  type AttributeCatalog,
  type Comparability,
  type NpsSummary,
  type OpenAnswer,
  type QuestionResult,
  type ResponseRate,
  type Retention,
  type SurveyResults,
} from "./schemas/results";

export {
  EXPORT_NOTICE,
  NEVER_PUBLISHED_DESCRIPTION,
  NEVER_PUBLISHED_TITLE,
  NO_DISPLAYS_DESCRIPTION,
  NO_DISPLAYS_TITLE,
  NO_MATCHES_DESCRIPTION,
  NO_MATCHES_TITLE,
  OPEN_ANSWERS_NOTICE,
  formatRate,
  formatShare,
  resultsEmptyVariant,
  retentionNoteText,
  type ResultsEmptyVariant,
} from "./lib/results-labels";

export { ResultsFiltersForm } from "./components/results-filters";
export { ActiveFilters } from "./components/active-filters";
export { ResponseRateCard } from "./components/response-rate-card";
export { QuestionResultCard } from "./components/question-result-card";
export { NpsSummaryCard } from "./components/nps-summary-card";
export { OpenAnswersList } from "./components/open-answers-list";
export { OpenAnswersSearch } from "./components/open-answers-search";
export { RefreshControl } from "./components/refresh-control";
export { ExportButton } from "./components/export-button";
export { RetentionNote } from "./components/retention-note";
