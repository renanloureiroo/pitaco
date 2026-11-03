import Link from "next/link";
import { notFound } from "next/navigation";

import { Button } from "@/components/ui/button";
import {
  DisplayFiltersForm,
  DisplaysTable,
  displaysEmptyVariant,
  listSurveyDisplays,
  parseDisplayFilters,
} from "@/features/collect";
import { getSurvey, getVersionComparability } from "@/features/surveys";
import { ApiUnavailableError, parsePaginationParams } from "@/shared/api";
import { EmptyState, Pagination } from "@/shared/components";
import { TIMEZONE_NOTE } from "@/shared/lib";

export const metadata = { title: "Exibições" };

export default async function SurveyDisplaysPage({
  params,
  searchParams,
}: PageProps<"/aplicacoes/[applicationId]/pesquisas/[surveyId]/exibicoes">) {
  const { applicationId, surveyId } = await params;
  const query = await searchParams;
  const { page, size } = parsePaginationParams(query);
  const filters = parseDisplayFilters(query);

  const [surveyResult, displaysResult, comparabilityResult] = await Promise.all([
    getSurvey(applicationId, surveyId),
    listSurveyDisplays(applicationId, surveyId, { page, size, ...filters }),
    getVersionComparability(applicationId, surveyId),
  ]);

  if (!surveyResult.ok) {
    if (surveyResult.kind === "not_found") {
      notFound();
    }
    throw new ApiUnavailableError(surveyResult);
  }

  if (!displaysResult.ok) {
    if (displaysResult.kind === "not_found") {
      notFound();
    }
    throw new ApiUnavailableError(displaysResult);
  }

  if (!comparabilityResult.ok) {
    throw new ApiUnavailableError(comparabilityResult);
  }

  const survey = surveyResult.data;
  const displays = displaysResult.data;
  const versions = comparabilityResult.data.groups
    .flatMap((group) => group.versions)
    .sort((a, b) => b - a);

  const variant = displaysEmptyVariant({
    ...(survey.publishedVersionNumber !== undefined
      ? { publishedVersionNumber: survey.publishedVersionNumber }
      : {}),
    filters,
  });

  return (
    <section className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h2 className="font-heading text-lg font-medium">Exibições</h2>
        <p data-testid="timezone-note" className="text-sm text-muted-foreground">
          {TIMEZONE_NOTE}
        </p>
      </div>

      {variant === "never_published" ? null : (
        <DisplayFiltersForm filters={filters} versions={versions} />
      )}

      {displays.items.length === 0 ? (
        <EmptyVariant
          variant={variant}
          publicationHref={`/aplicacoes/${applicationId}/pesquisas/${surveyId}/publicacao`}
          listHref={`/aplicacoes/${applicationId}/pesquisas/${surveyId}/exibicoes`}
        />
      ) : (
        <>
          <DisplaysTable applicationId={applicationId} displays={displays.items} />
          <Pagination
            pathname={`/aplicacoes/${applicationId}/pesquisas/${surveyId}/exibicoes`}
            searchParams={query}
            page={displays}
          />
        </>
      )}
    </section>
  );
}

function EmptyVariant({
  variant,
  publicationHref,
  listHref,
}: {
  variant: "never_published" | "no_displays" | "no_matches";
  publicationHref: string;
  listHref: string;
}) {
  if (variant === "never_published") {
    return (
      <EmptyState
        title="Esta pesquisa ainda não foi publicada"
        description="Só uma versão publicada pode ser exibida a quem usa a aplicação."
        action={
          <Button asChild>
            <Link href={publicationHref}>Ir para a publicação</Link>
          </Button>
        }
      />
    );
  }

  if (variant === "no_matches") {
    return (
      <EmptyState
        title="Nenhuma exibição neste recorte"
        description="A pesquisa tem exibições, mas nenhuma atende aos filtros aplicados."
        action={
          <Button asChild variant="outline">
            <Link href={listHref}>Limpar filtros</Link>
          </Button>
        }
      />
    );
  }

  return (
    <EmptyState
      title="Nenhuma exibição ainda"
      description="A pesquisa está no ar e ainda não foi vista por ninguém."
    />
  );
}
