import { KeyRoundIcon } from "lucide-react";

import {
  API_KEY_STATUSES,
  ApiKeysTable,
  IssueKeyForm,
  RevokeKeyButton,
  listApiKeys,
} from "@/features/api-keys";
import { ApiUnavailableError, parsePaginationParams, parseStatusParam } from "@/shared/api";
import { EmptyState, Pagination } from "@/shared/components";

export const metadata = { title: "Chaves de acesso" };

export default async function ApiKeysPage({
  params,
  searchParams,
}: PageProps<"/aplicacoes/[applicationId]/chaves">) {
  const { applicationId } = await params;
  const query = await searchParams;
  const { page, size } = parsePaginationParams(query);
  const status = parseStatusParam(query.status, API_KEY_STATUSES);

  const result = await listApiKeys(applicationId, { status, page, size });

  if (!result.ok) {
    throw new ApiUnavailableError(result);
  }

  const apiKeys = result.data;

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="font-heading text-2xl font-semibold tracking-tight">Chaves de acesso</h1>
        <IssueKeyForm applicationId={applicationId} />
      </div>

      {apiKeys.items.length === 0 ? (
        <EmptyState
          icon={<KeyRoundIcon aria-hidden />}
          title="Nenhuma chave emitida"
          description="Emita uma chave para que uma aplicação possa coletar respostas. O segredo aparece uma única vez."
          action={<IssueKeyForm applicationId={applicationId} />}
        />
      ) : (
        <>
          <ApiKeysTable
            apiKeys={apiKeys.items}
            actionsFor={(apiKey) =>
              apiKey.status === "active" ? (
                <RevokeKeyButton
                  applicationId={applicationId}
                  apiKeyId={apiKey.id}
                  label={apiKey.label}
                />
              ) : null
            }
          />
          <Pagination
            pathname={`/aplicacoes/${applicationId}/chaves`}
            searchParams={query}
            page={apiKeys}
          />
        </>
      )}
    </div>
  );
}
