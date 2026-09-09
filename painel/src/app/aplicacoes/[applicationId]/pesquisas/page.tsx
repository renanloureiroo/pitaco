import Link from "next/link";
import { ListChecksIcon, PlusIcon } from "lucide-react";

import { Button } from "@/components/ui/button";
import { SurveysTable, listSurveys } from "@/features/surveys";
import { ApiUnavailableError, parsePaginationParams } from "@/shared/api";
import { EmptyState, Pagination } from "@/shared/components";

export const metadata = { title: "Pesquisas" };

export default async function SurveysPage({
  params,
  searchParams,
}: PageProps<"/aplicacoes/[applicationId]/pesquisas">) {
  const { applicationId } = await params;
  const query = await searchParams;
  const { page, size } = parsePaginationParams(query);

  // A listagem é escopada pela aplicação: não existe listagem global de pesquisa (FR-017).
  const result = await listSurveys(applicationId, { page, size });

  if (!result.ok) {
    throw new ApiUnavailableError(result);
  }

  const surveys = result.data;
  const basePath = `/aplicacoes/${applicationId}/pesquisas`;

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="font-heading text-2xl font-semibold tracking-tight">Pesquisas</h1>
        <Button asChild data-testid="new-survey-link">
          <Link href={`${basePath}/nova`}>
            <PlusIcon aria-hidden />
            Nova pesquisa
          </Link>
        </Button>
      </div>

      {surveys.items.length === 0 ? (
        <EmptyState
          icon={<ListChecksIcon aria-hidden />}
          title="Nenhuma pesquisa nesta aplicação"
          description="Crie a primeira pesquisa para montar perguntas, configurar o disparo e publicar."
          action={
            <Button asChild>
              <Link href={`${basePath}/nova`}>Nova pesquisa</Link>
            </Button>
          }
        />
      ) : (
        <>
          <SurveysTable applicationId={applicationId} surveys={surveys.items} />
          <Pagination pathname={basePath} searchParams={query} page={surveys} />
        </>
      )}
    </div>
  );
}
