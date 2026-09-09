import { notFound } from "next/navigation";

import { TriggerPanel, getSurvey, isAssemblyReadOnly } from "@/features/surveys";
import { ApiUnavailableError } from "@/shared/api";

export const metadata = { title: "Disparo" };

export default async function TriggerPage({
  params,
}: PageProps<"/aplicacoes/[applicationId]/pesquisas/[surveyId]/disparo">) {
  const { applicationId, surveyId } = await params;
  const result = await getSurvey(applicationId, surveyId);

  if (!result.ok) {
    if (result.kind === "not_found") {
      notFound();
    }
    throw new ApiUnavailableError(result);
  }

  const survey = result.data;

  return (
    <section className="flex flex-col gap-4">
      <h2 className="font-heading text-lg font-medium">Disparo e segmentação</h2>
      <TriggerPanel
        applicationId={applicationId}
        surveyId={surveyId}
        trigger={survey.content?.trigger}
        readOnly={isAssemblyReadOnly(survey)}
      />
    </section>
  );
}
