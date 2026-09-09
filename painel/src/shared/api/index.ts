/** Fronteira pública do compartilhado de API: cliente HTTP, erro tipado e paginação. */

export { API_URL_ENV_VAR, MissingApiUrlError, getApiBaseUrl } from "./env";

export {
  ApiUnavailableError,
  describeFailure,
  statusToKind,
  toApiFailure,
  unreachableFailure,
  type ApiFailure,
  type ApiFailureKind,
  type ProblemDetails,
  type Result,
} from "./errors";

export { buildUrl, request, requestNoContent, type QueryValue, type RequestOptions } from "./client";

export {
  DEFAULT_PAGE,
  DEFAULT_SIZE,
  MAX_SIZE,
  MIN_SIZE,
  pageResponseSchema,
  parsePaginationParams,
  parseStatusParam,
  type PageResponse,
  type PaginationParams,
  type RawSearchParam,
} from "./pagination";
