import Link from "next/link";
import { notFound } from "next/navigation";
import {
  ActivityIcon,
  BugIcon,
  CheckCheckIcon,
  EyeIcon,
  GaugeIcon,
  ListChecksIcon,
  PercentIcon,
  UsersIcon,
} from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  PeriodTabs,
  SdkAdoption,
  SegmentBreakdown,
  SurveyPerformanceTable,
  getApplicationOverview,
  getSegmentBreakdown,
  parseOverviewPeriod,
  pointChange,
  relativeChange,
  OVERVIEW_PERIOD_LABELS,
} from "@/features/analytics";
import {
  ApplicationDetail,
  ApplicationStatusButton,
  EditApplicationDialog,
  getApplication,
} from "@/features/applications";
import { ApiUnavailableError } from "@/shared/api";
import { ChartCard, EmptyState, KpiCard, NpsBreakdown, OutcomeBreakdown, TrendChart } from "@/shared/components";
import { formatInt, formatNps, formatPercent } from "@/shared/lib";

export const metadata = { title: "Visão geral" };

const SEGMENT_PARAM = "segmento";

/**
 * Visão geral da aplicação: KPIs do período contra o período anterior, evolução diária,
 * desfechos, NPS consolidado, análise por segmento e desempenho por pesquisa. Casca fina — a
 * soma vive na feature de análise; aqui só a composição.
 */
export default async function ApplicationOverviewPage({
  params,
  searchParams,
}: PageProps<"/aplicacoes/[applicationId]">) {
  const { applicationId } = await params;
  const query = await searchParams;
  const period = parseOverviewPeriod(query.periodo);

  const [applicationResult, overviewResult] = await Promise.all([
    getApplication(applicationId),
    getApplicationOverview(applicationId, period),
  ]);

  if (!applicationResult.ok) {
    // 404 cobre identificador inexistente e malformado: a mesma tela serve para os dois.
    if (applicationResult.kind === "not_found") {
      notFound();
    }
    throw new ApiUnavailableError(applicationResult);
  }
  if (!overviewResult.ok) {
    throw new ApiUnavailableError(overviewResult);
  }

  const application = applicationResult.data;
  const overview = overviewResult.data;
  const base = `/aplicacoes/${applicationId}`;
  const { totals, previous } = overview;
  const surveyCount = overview.surveys.length;
  const liveCount = overview.stateCounts.active ?? 0;

  // Segmento: o atributo pedido na URL, ou o primeiro com poucos valores (bom para comparar).
  const attributes = overview.featured?.attributes ?? [];
  const rawSegment = Array.isArray(query[SEGMENT_PARAM]) ? query[SEGMENT_PARAM][0] : query[SEGMENT_PARAM];
  const segmentAttribute =
    attributes.find((attribute) => attribute.name === rawSegment) ??
    [...attributes].filter((attribute) => attribute.values.length >= 2).sort((a, b) => a.values.length - b.values.length)[0];
  const segments =
    overview.featured !== undefined && segmentAttribute !== undefined
      ? await getSegmentBreakdown(applicationId, overview.featured.id, segmentAttribute, period)
      : [];

  const displayedChange = relativeChange(totals.displayed, previous.displayed);
  const rateChange = pointChange(totals.rate, previous.rate);
  const npsChange = pointChange(overview.nps?.score, overview.previousNps?.score);
  const abandonShare = totals.displayed > 0 ? totals.abandoned / totals.displayed : undefined;
  const previousAbandonShare = previous.displayed > 0 ? previous.abandoned / previous.displayed : undefined;
  const abandonChange = pointChange(abandonShare, previousAbandonShare);

  return (
    <div className="flex flex-col gap-6" data-testid="application-overview">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex flex-col gap-1">
          <h1 className="text-2xl font-semibold tracking-tight">{application.name}</h1>
          <p className="text-[13px] text-ink-muted">
            Visão geral · {surveyCount} pesquisa{surveyCount === 1 ? "" : "s"}, {liveCount} no ar ·{" "}
            {formatInt(overview.respondents)} respondentes conhecidos
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <PeriodTabs pathname={base} period={period} searchParams={{ [SEGMENT_PARAM]: rawSegment }} />
          <EditApplicationDialog application={application} />
          <ApplicationStatusButton application={application} />
        </div>
      </header>

      {surveyCount === 0 ? (
        <EmptyState
          icon={<ListChecksIcon aria-hidden />}
          title="Nenhuma pesquisa ainda"
          description="Crie a primeira pesquisa para começar a ouvir quem usa o app. Os números aparecem aqui assim que ela for exibida."
          action={
            <Button asChild>
              <Link href={`${base}/pesquisas/nova`}>Nova pesquisa</Link>
            </Button>
          }
        />
      ) : (
        <>
          <section aria-label="Indicadores" className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <KpiCard
              testId="kpi-displayed"
              label="Exibições"
              icon={<EyeIcon aria-hidden />}
              value={formatInt(totals.displayed)}
              {...(displayedChange !== undefined
                ? { delta: { value: displayedChange, unit: "relative" as const, goodWhen: "up" as const } }
                : {})}
              note={`vs. ${OVERVIEW_PERIOD_LABELS[period]} anteriores`}
            />
            <KpiCard
              testId="kpi-rate"
              label="Taxa de resposta"
              icon={<PercentIcon aria-hidden />}
              value={formatPercent(totals.rate)}
              {...(rateChange !== undefined ? { delta: { value: rateChange, unit: "points" as const, goodWhen: "up" as const } } : {})}
              note={`${formatInt(totals.completed)} concluídas`}
            />
            <KpiCard
              testId="kpi-nps"
              label="NPS"
              icon={<GaugeIcon aria-hidden />}
              value={formatNps(overview.nps?.score)}
              {...(npsChange !== undefined ? { delta: { value: npsChange, unit: "nps" as const, goodWhen: "up" as const } } : {})}
              note={overview.nps === undefined ? "nenhuma pesquisa de NPS" : `${formatInt(overview.nps.respondents)} respostas`}
            />
            <KpiCard
              testId="kpi-abandon"
              label="Abandono"
              icon={<ActivityIcon aria-hidden />}
              value={formatPercent(abandonShare)}
              {...(abandonChange !== undefined
                ? { delta: { value: abandonChange, unit: "points" as const, goodWhen: "down" as const } }
                : {})}
              note={`${formatInt(totals.abandoned)} exibições`}
            />
          </section>

          <div className="grid grid-cols-1 gap-4 xl:grid-cols-3">
            <ChartCard
              className="xl:col-span-2"
              title="Exibições e conclusões"
              description={`Por dia de abertura (UTC), ${OVERVIEW_PERIOD_LABELS[period]}`}
            >
              <TrendChart points={overview.timeline} />
            </ChartCard>
            <ChartCard title="Para onde vão as exibições" description="Desfecho de cada exibição no período">
              <OutcomeBreakdown counts={totals} />
            </ChartCard>
          </div>

          <div className="grid grid-cols-1 gap-4 xl:grid-cols-3">
            <ChartCard title="NPS consolidado" description="Todas as pesquisas criadas do modelo NPS">
              <NpsBreakdown nps={overview.nps} />
            </ChartCard>
            <ChartCard
              className="xl:col-span-2"
              title="Análise por segmento"
              description={
                overview.featured === undefined
                  ? "Precisa de uma pesquisa de NPS com respostas no período"
                  : `${overview.featured.name}: NPS e taxa por valor do atributo`
              }
              actions={
                attributes.length > 0 ? (
                  <nav aria-label="Atributo" className="flex flex-wrap gap-1">
                    {attributes.map((attribute) => (
                      <Link
                        key={attribute.name}
                        href={`${base}?periodo=${period}&${SEGMENT_PARAM}=${encodeURIComponent(attribute.name)}`}
                        aria-current={attribute.name === segmentAttribute?.name ? "true" : undefined}
                        className="rounded-sm px-2 py-1 font-mono text-xs text-ink-muted transition-colors hover:bg-surface-raised hover:text-foreground aria-[current=true]:bg-primary-soft aria-[current=true]:text-primary-ink"
                      >
                        {attribute.name}
                      </Link>
                    ))}
                  </nav>
                ) : null
              }
            >
              {segmentAttribute === undefined ? (
                <p className="text-sm text-ink-muted">
                  Nenhum atributo recebido no período. O app envia atributos (plano, plataforma…) junto do evento.
                </p>
              ) : (
                <SegmentBreakdown rows={segments} attribute={segmentAttribute.name} />
              )}
            </ChartCard>
          </div>

          <ChartCard
            title="Desempenho por pesquisa"
            description={`${OVERVIEW_PERIOD_LABELS[period]}, da mais exibida para a menos — a variação da taxa compara com o período anterior`}
            actions={
              <Button asChild variant="outline" size="sm">
                <Link href={`${base}/pesquisas`}>Ver todas</Link>
              </Button>
            }
          >
            <SurveyPerformanceTable applicationId={applicationId} surveys={overview.surveys} />
          </ChartCard>

          <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
            <KpiCard
              label="Respondentes conhecidos"
              icon={<UsersIcon aria-hidden />}
              value={formatInt(overview.respondents)}
              note={
                <Link href={`${base}/respondentes`} className="hover:underline">
                  Ver respondentes
                </Link>
              }
            />
            <ChartCard title="Versões do SDK" description="Requisições na janela recente">
              <SdkAdoption sdk={overview.sdk} />
            </ChartCard>
            <KpiCard
              label="Erros do SDK"
              icon={<BugIcon aria-hidden />}
              value={formatInt(overview.sdkErrors)}
              note={
                <Link href={`${base}/saude`} className="hover:underline">
                  Ver a saúde do SDK
                </Link>
              }
            />
          </div>
        </>
      )}

      <section aria-label="Configuração da aplicação" className="flex flex-col gap-3">
        <h2 className="flex items-center gap-2 text-lg font-semibold">
          <CheckCheckIcon aria-hidden className="size-4 text-ink-muted" />
          Configuração
        </h2>
        <ApplicationDetail application={application} />
      </section>
    </div>
  );
}
