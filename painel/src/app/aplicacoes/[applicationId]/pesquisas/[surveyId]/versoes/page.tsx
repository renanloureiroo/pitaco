import { notFound } from "next/navigation";

import {
  ComparabilityPanel,
  DraftVersionActions,
  VersionsTable,
  getSurvey,
  getVersionComparability,
  listVersions,
} from "@/features/surveys";
import { ApiUnavailableError, parsePaginationParams } from "@/shared/api";
import { EmptyState, Pagination } from "@/shared/components";

export const metadata = { title: "Versões" };

export default async function VersionsPage({
  params,
  searchParams,
}: PageProps<"/aplicacoes/[applicationId]/pesquisas/[surveyId]/versoes">) {
  const { applicationId, surveyId } = await params;
  const query = await searchParams;
  const { page, size } = parsePaginationParams(query);

  const [surveyResult, versionsResult, comparabilityResult] = await Promise.all([
    getSurvey(applicationId, surveyId),
    listVersions(applicationId, surveyId, { page, size }),
    getVersionComparability(applicationId, surveyId),
  ]);

  if (!surveyResult.ok) {
    if (surveyResult.kind === "not_found") {
      notFound();
    }
    throw new ApiUnavailableError(surveyResult);
  }

  if (!versionsResult.ok) {
    throw new ApiUnavailableError(versionsResult);
  }

  if (!comparabilityResult.ok) {
    throw new ApiUnavailableError(comparabilityResult);
  }

  const survey = surveyResult.data;
  const versions = versionsResult.data;

  return (
    <section className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h2 className="font-heading text-lg font-medium">Versões</h2>
        <DraftVersionActions
          applicationId={applicationId}
          surveyId={surveyId}
          hasDraft={survey.draftVersionNumber !== undefined}
          canOpenDraft={survey.publishedVersionNumber !== undefined}
        />
      </div>

      {versions.items.length === 0 ? (
        <EmptyState
          title="Nenhuma versão ainda"
          description="A primeira versão nasce quando a pesquisa é publicada."
        />
      ) : (
        <>
          <VersionsTable
            applicationId={applicationId}
            surveyId={surveyId}
            versions={versions.items}
          />
          <Pagination
            pathname={`/aplicacoes/${applicationId}/pesquisas/${surveyId}/versoes`}
            searchParams={query}
            page={versions}
          />
        </>
      )}

      <ComparabilityPanel comparability={comparabilityResult.data} />
    </section>
  );
}
