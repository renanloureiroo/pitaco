/** Fronteira pública da feature de aplicações (Princípio I). */

export {
  listApplications,
  getApplication,
  listObservedAttributes,
  listObservedEvents,
} from "./api";

export {
  createApplicationAction,
  setApplicationStatusAction,
  updateApplicationAction,
} from "./actions";

export {
  APPLICATION_STATUSES,
  APPLICATION_STATUS_LABELS,
  type Application,
  type ApplicationStatus,
  type ApplicationSummary,
  type ObservedAttribute,
  type ObservedEvent,
} from "./schemas/application";

export { applicationListParamsSchema } from "./schemas/forms";

export { ApplicationsTable } from "./components/applications-table";
export { ApplicationStatusFilter } from "./components/application-status-filter";
export { ApplicationForm } from "./components/application-form";
export { ApplicationDetail } from "./components/application-detail";
export { EditApplicationDialog } from "./components/edit-application-dialog";
export { ApplicationStatusButton } from "./components/application-status-button";
