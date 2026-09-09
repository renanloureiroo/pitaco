/** Fronteira pública da feature de aplicações (Princípio I). */

export { listApplications, getApplication } from "./api";

export { createApplicationAction } from "./actions";

export {
  APPLICATION_STATUSES,
  APPLICATION_STATUS_LABELS,
  type Application,
  type ApplicationStatus,
  type ApplicationSummary,
} from "./schemas/application";

export { applicationListParamsSchema } from "./schemas/forms";

export { ApplicationsTable } from "./components/applications-table";
export { ApplicationStatusFilter } from "./components/application-status-filter";
export { ApplicationForm } from "./components/application-form";
export { ApplicationDetail } from "./components/application-detail";
