import { SurveyForm } from "@/features/surveys";

export const metadata = { title: "Nova pesquisa" };

export default async function NewSurveyPage({
  params,
}: PageProps<"/aplicacoes/[applicationId]/pesquisas/nova">) {
  const { applicationId } = await params;

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-1">
        <h1 className="font-heading text-2xl font-semibold tracking-tight">Nova pesquisa</h1>
        <p className="text-sm text-muted-foreground">
          A pesquisa nasce em rascunho, sem perguntas e sem disparo.
        </p>
      </div>
      <SurveyForm applicationId={applicationId} />
    </div>
  );
}
