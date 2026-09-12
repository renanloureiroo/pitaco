/** Fronteira pública da feature de saúde e compatibilidade (Princípio I). */

export { getSdkVersions, getSurveyHealth, listSdkErrors } from "./api/health";

export {
  KIND_PARAM,
  SDK_VERSION_PARAM,
  parseSdkErrorFilters,
  type SdkErrorFilters,
} from "./schemas/filters";

export {
  SDK_ERROR_KINDS,
  type SdkErrorKind,
  type SdkErrorReport,
  type SdkVersionUsage,
  type SdkVersions,
  type SurveyHealth,
} from "./schemas/health";

export {
  SDK_ERRORS_NOTE,
  SDK_ERROR_KIND_LABELS,
  STALE_NOTE,
  surveyHealthNotices,
  type HealthNotice,
} from "./lib/health-labels";

export { SdkVersionsTable } from "./components/sdk-versions-table";
export { SdkErrorsList } from "./components/sdk-errors-list";
export { SdkErrorFiltersForm } from "./components/sdk-error-filters";
export { SurveyHealthNotices } from "./components/survey-health-notices";
