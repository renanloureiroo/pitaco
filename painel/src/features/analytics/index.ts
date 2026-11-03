/** Fronteira pública da feature de análise: visão geral da aplicação e análise por segmento. */

export { getApplicationOverview, getSegmentBreakdown, type ApplicationOverview, type SegmentRow } from "./api/overview";

export {
  DEFAULT_OVERVIEW_PERIOD,
  OVERVIEW_PERIODS,
  OVERVIEW_PERIOD_LABELS,
  mergeTimelines,
  parseOverviewPeriod,
  pointChange,
  relativeChange,
  sumNps,
  sumTotals,
  type NpsTotals,
  type OverviewPeriod,
  type SurveyPerformance,
  type Totals,
} from "./lib/aggregate";

export { SurveyPerformanceTable } from "./components/survey-performance-table";
export { SegmentBreakdown } from "./components/segment-breakdown";
export { PeriodTabs } from "./components/period-tabs";
export { SdkAdoption } from "./components/sdk-adoption";
export { Sparkline } from "./components/sparkline";
