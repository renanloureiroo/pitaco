import { notFound } from "next/navigation";

import { QuestionsPanel, getSurvey, isAssemblyReadOnly } from "@/features/surveys";
import { ApiUnavailableError } from "@/shared/api";

export default async function SurveyAssemblyPage({
  params,
}: PageProps<"/aplicacoes/[applicationId]/pesquisas/[surveyId]">) {
  const { applicationId, surveyId } = await params;
  const result = await getSurvey(applicationId, surveyId);

  if (!result.ok) {
    if (result.kind === "not_found") {
      notFound();
    }
    throw new ApiUnavailableError(result);
  }

  const survey = result.data;
  // A decisão vem de `content.source` e do estado, ambos do backend — não de dedução própria.
  const readOnly = isAssemblyReadOnly(survey);

  return (
    <section className="flex flex-col gap-4">
      <div className="flex flex-col gap-1">
        <h2 className="font-heading text-lg font-medium">Perguntas</h2>
        {readOnly ? (
          <p className="text-sm text-muted-foreground">
            {survey.state === "ended"
              ? "A pesquisa está encerrada: o conteúdo é somente leitura."
              : "Este é o conteúdo publicado. Abra uma nova versão de rascunho em Versões para editar."}
          </p>
        ) : null}
      </div>

      <QuestionsPanel
        applicationId={applicationId}
        surveyId={surveyId}
        questions={survey.content?.questions ?? []}
        readOnly={readOnly}
      />
    </section>
  );
}
