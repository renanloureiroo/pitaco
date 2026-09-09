import {
  conflict,
  json,
  nextId,
  noContent,
  notFound,
  nowIso,
  paginate,
  readPageQuery,
  validation,
  type Route,
} from "../http.ts";
import { store, type StubApiKey } from "../store.ts";

/** O segredo fica no estado, mas **nunca** sai numa leitura — só na resposta do POST. */
function toApiKey(apiKey: StubApiKey) {
  const { id, applicationId, label, prefix, status, createdAt, revokedAt } = apiKey;
  return {
    id,
    applicationId,
    label,
    prefix,
    status,
    createdAt,
    ...(revokedAt !== undefined ? { revokedAt } : {}),
  };
}

function keyOf(applicationId: string, apiKeyId: string): StubApiKey | undefined {
  const apiKey = store.apiKeys.get(apiKeyId);
  return apiKey !== undefined && apiKey.applicationId === applicationId ? apiKey : undefined;
}

export const apiKeyRoutes: Route[] = [
  {
    method: "GET",
    pattern: "/applications/:applicationId/api-keys",
    handler: ({ params, query }) => {
      const status = query.get("status");

      const items = [...store.apiKeys.values()]
        .filter((apiKey) => apiKey.applicationId === params.applicationId)
        .filter((apiKey) => status === null || apiKey.status === status)
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
        .map(toApiKey);

      return json(200, paginate(items, readPageQuery(query)));
    },
  },
  {
    method: "GET",
    pattern: "/applications/:applicationId/api-keys/:apiKeyId",
    handler: ({ params }) => {
      const apiKey = keyOf(params.applicationId, params.apiKeyId);
      return apiKey === undefined
        ? notFound("api_key.not_found", "Chave não encontrada.")
        : json(200, toApiKey(apiKey));
    },
  },
  {
    method: "POST",
    pattern: "/applications/:applicationId/api-keys",
    handler: ({ params, body }) => {
      if (!store.applications.has(params.applicationId)) {
        return notFound("application.not_found", "Aplicação não encontrada.");
      }

      const label = typeof (body as { label?: unknown })?.label === "string"
        ? String((body as { label: string }).label).trim()
        : "";

      if (label === "") {
        return validation("request.invalid", "Requisição inválida.", {
          label: "O rótulo é obrigatório.",
        });
      }

      const prefix = `pit_${nextId("k").slice(2, 10)}`;
      const apiKey: StubApiKey = {
        id: nextId("key"),
        applicationId: params.applicationId,
        label,
        prefix,
        secret: `${prefix}.${nextId("s").slice(2)}${nextId("s").slice(2)}`,
        status: "active",
        createdAt: nowIso(),
      };

      store.apiKeys.set(apiKey.id, apiKey);

      return json(201, {
        id: apiKey.id,
        applicationId: apiKey.applicationId,
        label: apiKey.label,
        prefix: apiKey.prefix,
        createdAt: apiKey.createdAt,
        secret: apiKey.secret,
      });
    },
  },
  {
    method: "DELETE",
    pattern: "/applications/:applicationId/api-keys/:apiKeyId",
    handler: ({ params }) => {
      const apiKey = keyOf(params.applicationId, params.apiKeyId);

      if (apiKey === undefined) {
        return notFound("api_key.not_found", "Chave não encontrada.");
      }

      if (apiKey.status === "revoked") {
        return conflict("api_key.already_revoked", "Esta chave já está revogada.");
      }

      apiKey.status = "revoked";
      apiKey.revokedAt = nowIso();

      return noContent();
    },
  },
];
