import Link from "next/link";
import { notFound } from "next/navigation";

import { Button } from "@/components/ui/button";
import {
  DisplayFiltersForm,
  DisplaysTable,
  RespondentSummary,
  historyEmptyVariant,
  listRespondentDisplays,
  parseDisplayFilters,
} from "@/features/collect";
import { ApiUnavailableError, parsePaginationParams } from "@/shared/api";
import { EmptyState, Pagination } from "@/shared/components";
import { TIMEZONE_NOTE } from "@/shared/lib";

export const metadata = { title: "Histórico do respondente" };

export default async function RespondentHistoryPage({
  params,
  searchParams,
}: PageProps<"/aplicacoes/[applicationId]/respondentes/[respondentId]">) {
  const { applicationId, respondentId } = await params;
  const query = await searchParams;
  const { page, size } = parsePaginationParams(query);
  const { versionNumber: _versionIgnored, ...filters } = parseDisplayFilters(query);

  const result = await listRespondentDisplays(applicationId, respondentId, {
    page,
    size,
    ...filters,
  });

  if (!result.ok) {
    if (result.kind === "not_found") {
      notFound();
    }
    throw new ApiUnavailableError(result);
  }

  const displays = result.data;
  const listHref = `/aplicacoes/${applicationId}/respondentes/${respondentId}`;

  return (
    <section className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h2 className="font-heading text-lg font-medium">Histórico do respondente</h2>
        <p data-testid="timezone-note" className="text-sm text-muted-foreground">
          {TIMEZONE_NOTE}
        </p>
      </div>

      <RespondentSummary applicationId={applicationId} respondentId={respondentId} />

      <div data-testid="respondent-displays" className="flex flex-col gap-6">
        <DisplayFiltersForm filters={filters} />

        {displays.items.length === 0 ? (
          historyEmptyVariant(filters) === "no_matches" ? (
            <EmptyState
              title="Nenhuma exibição neste recorte"
              description="Este respondente tem exibições, mas nenhuma atende aos filtros aplicados."
              action={
                <Button asChild variant="outline">
                  <Link href={listHref}>Limpar filtros</Link>
                </Button>
              }
            />
          ) : (
            <EmptyState
              title="Este respondente ainda não recebeu exibição"
              description="Ele já foi visto pela aplicação, mas nenhuma pesquisa lhe foi exibida."
            />
          )
        ) : (
          <>
            <DisplaysTable
              applicationId={applicationId}
              displays={displays.items}
              showSurvey
            />
            <Pagination pathname={listHref} searchParams={query} page={displays} />
          </>
        )}
      </div>
    </section>
  );
}
