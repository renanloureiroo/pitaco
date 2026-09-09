import {
  pageResponseSchema,
  request,
  requestNoContent,
  type PageResponse,
  type Result,
} from "@/shared/api";

import {
  apiKeySchema,
  issuedApiKeySchema,
  type ApiKey,
  type ApiKeyStatus,
  type IssuedApiKey,
} from "../schemas/api-key";

function apiKeysPath(applicationId: string): string {
  return `/applications/${encodeURIComponent(applicationId)}/api-keys`;
}

const apiKeyPageSchema = pageResponseSchema(apiKeySchema);

export function listApiKeys(
  applicationId: string,
  params: { status?: ApiKeyStatus; page: number; size: number },
): Promise<Result<PageResponse<ApiKey>>> {
  return request(apiKeyPageSchema, {
    path: apiKeysPath(applicationId),
    query: { status: params.status, page: params.page, size: params.size },
  });
}

export function getApiKey(
  applicationId: string,
  apiKeyId: string,
): Promise<Result<ApiKey>> {
  return request(apiKeySchema, {
    path: `${apiKeysPath(applicationId)}/${encodeURIComponent(apiKeyId)}`,
  });
}

/** Único lugar do painel em que um segredo existe. Ele não é lido de volta em lugar nenhum. */
export function issueApiKey(
  applicationId: string,
  label: string,
): Promise<Result<IssuedApiKey>> {
  return request(issuedApiKeySchema, {
    path: apiKeysPath(applicationId),
    method: "POST",
    body: { label },
  });
}

export function revokeApiKey(
  applicationId: string,
  apiKeyId: string,
): Promise<Result<void>> {
  return requestNoContent({
    path: `${apiKeysPath(applicationId)}/${encodeURIComponent(apiKeyId)}`,
    method: "DELETE",
  });
}
