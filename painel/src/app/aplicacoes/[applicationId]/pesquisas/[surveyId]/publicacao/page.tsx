import { notFound } from "next/navigation";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  ImpedimentsList,
  PublishForm,
  getPublicationImpediments,
  getSurvey,
} from "@/features/surveys";
import { ApiUnavailableError } from "@/shared/api";

export const metadata = { title: "Publicação" };

export default async function PublicationPage({
  params,
}: PageProps<"/aplicacoes/[applicationId]/pesquisas/[surveyId]/publicacao">) {
  const { applicationId, surveyId } = await params;

  const [surveyResult, impedimentsResult] = await Promise.all([
    getSurvey(applicationId, surveyId),
    getPublicationImpediments(applicationId, surveyId),
  ]);

  if (!surveyResult.ok) {
    if (surveyResult.kind === "not_found") {
      notFound();
    }
    throw new ApiUnavailableError(surveyResult);
  }

  if (!impedimentsResult.ok) {
    throw new ApiUnavailableError(impedimentsResult);
  }

  const impediments = impedimentsResult.data;
  // A mesma lista que a publicação usaria para recusar: a tela nunca promete o que ela negaria.
  const hasImpediments = impediments.length > 0;

  return (
    <section className="flex flex-col gap-6">
      <Card>
        <CardHeader>
          <CardTitle>O que falta para publicar</CardTitle>
        </CardHeader>
        <CardContent>
          <ImpedimentsList
            applicationId={applicationId}
            surveyId={surveyId}
            impediments={impediments}
          />
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Publicar</CardTitle>
        </CardHeader>
        <CardContent>
          <PublishForm
            applicationId={applicationId}
            surveyId={surveyId}
            hasImpediments={hasImpediments}
            hasPublishedVersion={surveyResult.data.publishedVersionNumber !== undefined}
          />
        </CardContent>
      </Card>
    </section>
  );
}
