import Link from "next/link";
import { notFound } from "next/navigation";
import { ClockIcon, DoorOpenIcon, MousePointerClickIcon, RadarIcon } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  ActiveFilters,
  BehaviorDefinitions,
  DismissalChart,
  FrictionTable,
  NEVER_PUBLISHED_DESCRIPTION,
  NEVER_PUBLISHED_TITLE,
  QuestionFunnel,
  QuestionTimeChart,
  ResultsFiltersForm,
  changeRateOf,
  getSurveyBehavior,
  getSurveyResults,
  parseResultsFilters,
  toResultsQuery,
} from "@/features/results";
import { getVersionComparability } from "@/features/surveys";
import { ApiUnavailableError } from "@/shared/api";
import { EmptyState, KpiCard } from "@/shared/components";
import { formatDuration, formatInt, formatPercent } from "@/shared/lib";

export const metadata = { title: "Comportamento" };

/**
 * Comportamento dentro da pesquisa, a partir dos eventos de interação do SDK: funil por
 * pergunta, tempo ativo, vias de dispensa e sinais de atrito. Usa o mesmo recorte da URL que
 * os resultados — trocar de aba mantém período, atributo e versão.
 */
export default async function SurveyBehaviorPage({
  params,
  searchParams,
}: PageProps<"/aplicacoes/[applicationId]/pesquisas/[surveyId]/comportamento">) {
  const { applicationId, surveyId } = await params;
  const query = await searchParams;
  const filters = parseResultsFilters(query);
  const backendQuery = toResultsQuery(filters);

  const [behaviorResult, resultsResult, comparabilityResult] = await Promise.all([
    getSurveyBehavior(applicationId, surveyId, backendQuery),
    getSurveyResults(applicationId, surveyId, backendQuery),
    getVersionComparability(applicationId, surveyId),
  ]);

  if (!behaviorResult.ok) {
    if (behaviorResult.kind === "not_found") notFound();
    throw new ApiUnavailableError(behaviorResult);
  }
  if (!resultsResult.ok) {
    if (resultsResult.kind === "not_found") notFound();
    throw new ApiUnavailableError(resultsResult);
  }

  const behavior = behaviorResult.data;
  const base = `/aplicacoes/${applicationId}/pesquisas/${surveyId}`;
  const versions = comparabilityResult.ok
    ? comparabilityResult.data.groups.flatMap((group) => group.versions).sort((a, b) => b - a)
    : [];

  if (!behavior.everPublished) {
    return (
      <EmptyState
        title={NEVER_PUBLISHED_TITLE}
        description={NEVER_PUBLISHED_DESCRIPTION}
        action={
          <Button asChild>
            <Link href={`${base}/publicacao`}>Ir para a publicação</Link>
          </Button>
        }
      />
    );
  }

  const coverage = behavior.displayed > 0 ? behavior.instrumented / behavior.displayed : undefined;
  const medianTotal = behavior.questions.reduce((sum, q) => sum + (q.activeTime?.medianMs ?? 0), 0);
  const rates = behavior.questions.map(changeRateOf).filter((rate): rate is number => rate !== undefined);
  const changeRate = rates.length > 0 ? rates.reduce((sum, rate) => sum + rate, 0) / rates.length : undefined;
  const dismissShare = behavior.instrumented > 0 ? behavior.dismissals.total / behavior.instrumented : undefined;

  return (
    <section className="flex flex-col gap-6" data-testid="behavior-page">
      <div className="flex flex-col gap-1">
        <h2 className="text-lg font-semibold">Comportamento</h2>
        <p className="text-[13px] text-ink-muted">
          O que acontece dentro da pesquisa, pergunta a pergunta, medido pelos eventos de interação do SDK.
        </p>
      </div>

      <ResultsFiltersForm filters={filters} attributes={resultsResult.data.attributes} versions={versions} />
      <ActiveFilters filters={filters} />

      {behavior.instrumented === 0 ? (
        <EmptyState
          title="Nenhuma exibição instrumentada neste recorte"
          description="As exibições existem, mas nenhuma enviou eventos de interação. Versões antigas do SDK não enviam eventos."
        />
      ) : (
        <>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <KpiCard
              label="Instrumentadas"
              icon={<RadarIcon aria-hidden />}
              value={formatInt(behavior.instrumented)}
              note={`${formatPercent(coverage)} de ${formatInt(behavior.displayed)} exibições`}
            />
            <KpiCard
              label="Tempo ativo"
              icon={<ClockIcon aria-hidden />}
              value={formatDuration(medianTotal > 0 ? medianTotal : undefined)}
              note="soma das medianas por pergunta"
            />
            <KpiCard
              label="Troca de resposta"
              icon={<MousePointerClickIcon aria-hidden />}
              value={formatPercent(changeRate)}
              note="média entre as perguntas de escolha"
            />
            <KpiCard
              label="Dispensas"
              icon={<DoorOpenIcon aria-hidden />}
              value={formatInt(behavior.dismissals.total)}
              note={`${formatPercent(dismissShare)} das instrumentadas`}
            />
          </div>

          <QuestionFunnel behavior={behavior} />

          <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
            <QuestionTimeChart behavior={behavior} />
            <DismissalChart behavior={behavior} />
          </div>

          <FrictionTable behavior={behavior} />
          <BehaviorDefinitions behavior={behavior} />
        </>
      )}
    </section>
  );
}
