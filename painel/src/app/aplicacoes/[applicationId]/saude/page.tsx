import { notFound } from "next/navigation";

import {
  SDK_ERRORS_NOTE,
  STALE_NOTE,
  SdkErrorFiltersForm,
  SdkErrorsList,
  SdkVersionsTable,
  getSdkVersions,
  listSdkErrors,
  parseSdkErrorFilters,
} from "@/features/health";
import { ApiUnavailableError, parsePaginationParams } from "@/shared/api";
import { EmptyState, Pagination } from "@/shared/components";
import { TIMEZONE_NOTE } from "@/shared/lib";

export const metadata = { title: "Saúde" };

/**
 * O que acontece do outro lado: as versões do SDK que falam com a aplicação e as falhas que
 * elas reportam. Casca fina — leitura em paralelo e composição.
 */
export default async function ApplicationHealthPage({
  params,
  searchParams,
}: PageProps<"/aplicacoes/[applicationId]/saude">) {
  const { applicationId } = await params;
  const query = await searchParams;
  const filters = parseSdkErrorFilters(query);
  const { page, size } = parsePaginationParams(query);

  const [versionsResult, errorsResult] = await Promise.all([
    getSdkVersions(applicationId),
    listSdkErrors(applicationId, { page, size, ...filters }),
  ]);

  if (!versionsResult.ok) {
    if (versionsResult.kind === "not_found") {
      notFound();
    }
    throw new ApiUnavailableError(versionsResult);
  }
  if (!errorsResult.ok) {
    if (errorsResult.kind === "not_found") {
      notFound();
    }
    throw new ApiUnavailableError(errorsResult);
  }

  const versions = versionsResult.data;
  const errors = errorsResult.data;
  const filtered = filters.kind !== undefined || filters.sdkVersion !== undefined;

  return (
    <section className="flex flex-col gap-8">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h2 className="font-heading text-lg font-medium">Saúde e compatibilidade</h2>
        <p data-testid="timezone-note" className="text-sm text-muted-foreground">
          {TIMEZONE_NOTE}
        </p>
      </div>

      <section data-testid="sdk-versions" className="flex flex-col gap-3">
        <h3 className="font-heading text-base font-medium">Versões do SDK em uso</h3>
        <p className="text-sm text-muted-foreground">{STALE_NOTE}</p>
        {versions.versions.length === 0 ? (
          <EmptyState
            title="Nenhum SDK falou com esta aplicação ainda"
            description="Quando o app consultar a elegibilidade, cada versão do SDK aparece aqui com a sua fatia do tráfego."
          />
        ) : (
          <SdkVersionsTable versions={versions.versions} />
        )}
      </section>

      <section data-testid="sdk-errors" className="flex flex-col gap-3">
        <h3 className="font-heading text-base font-medium">Erros do SDK</h3>
        <p className="text-sm text-muted-foreground">{SDK_ERRORS_NOTE}</p>
        <SdkErrorFiltersForm filters={filters} />
        {errors.items.length === 0 ? (
          <p data-testid="sdk-errors-empty" className="text-sm text-muted-foreground">
            {filtered
              ? "Nenhum erro reportado neste recorte."
              : "Nenhum erro reportado pelo SDK desta aplicação."}
          </p>
        ) : (
          <>
            <SdkErrorsList errors={errors.items} />
            <Pagination
              pathname={`/aplicacoes/${applicationId}/saude`}
              searchParams={query}
              page={errors}
            />
          </>
        )}
      </section>
    </section>
  );
}
