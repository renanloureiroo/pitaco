import { listRespondents } from "@/features/collect";
import { getSdkVersions, listSdkErrors, type SdkVersions } from "@/features/health";
import { getSurveyResults, type AttributeCatalog, type SurveyResults, type TimelinePoint } from "@/features/results";
import { listSurveys, type Survey } from "@/features/surveys";
import type { Result } from "@/shared/api";

import {
  mergeTimelines,
  periodWindows,
  sumNps,
  sumTotals,
  surveyPerformance,
  type NpsTotals,
  type OverviewPeriod,
  type SurveyPerformance,
  type Totals,
} from "../lib/aggregate";

export type ApplicationOverview = {
  period: OverviewPeriod;
  totals: Totals;
  previous: Totals;
  nps?: NpsTotals;
  previousNps?: NpsTotals;
  timeline: TimelinePoint[];
  surveys: SurveyPerformance[];
  stateCounts: Record<string, number>;
  respondents: number;
  sdk?: SdkVersions;
  sdkErrors: number;
  /** A pesquisa de NPS com mais respostas: base da análise por segmento. */
  featured?: { id: string; name: string; attributes: AttributeCatalog[] };
};

const SURVEY_PAGE_SIZE = 100;

/**
 * Visão geral de uma aplicação no período: soma, no servidor do painel, o que o backend apura
 * por pesquisa. Nenhuma métrica é inventada aqui — só somas de contagens já apuradas.
 *
 * Falha na lista de pesquisas derruba a tela (sem ela não há o que somar); falha numa leitura
 * complementar (respondentes, SDK) só tira aquele bloco.
 */
export async function getApplicationOverview(
  applicationId: string,
  period: OverviewPeriod,
  now: Date = new Date(),
): Promise<Result<ApplicationOverview>> {
  const surveysResult = await listSurveys(applicationId, { page: 0, size: SURVEY_PAGE_SIZE });
  if (!surveysResult.ok) {
    return surveysResult;
  }
  const surveys = surveysResult.data.items;
  const published = surveys.filter((survey) => survey.publishedVersionNumber !== undefined);
  const windows = periodWindows(period, now);

  const [current, previous, respondents, sdk, sdkErrors] = await Promise.all([
    Promise.all(published.map((survey) => getSurveyResults(applicationId, survey.id, windows.current))),
    Promise.all(published.map((survey) => getSurveyResults(applicationId, survey.id, windows.previous))),
    listRespondents(applicationId, { page: 0, size: 1 }),
    getSdkVersions(applicationId),
    listSdkErrors(applicationId, { page: 0, size: 1 }),
  ]);

  const currentBySurvey = indexResults(published, current);
  const previousBySurvey = indexResults(published, previous);
  const currentList = [...currentBySurvey.values()];
  const previousList = [...previousBySurvey.values()];

  const performance = surveys
    .map((survey) => surveyPerformance(survey, currentBySurvey.get(survey.id), previousBySurvey.get(survey.id)))
    .sort((a, b) => b.totals.displayed - a.totals.displayed);

  const stateCounts: Record<string, number> = {};
  for (const survey of surveys) {
    stateCounts[survey.state] = (stateCounts[survey.state] ?? 0) + 1;
  }

  const featuredSurvey = published
    .map((survey) => ({ survey, results: currentBySurvey.get(survey.id) }))
    .filter((entry) => entry.results?.nps !== undefined)
    .sort((a, b) => (b.results?.nps?.respondents ?? 0) - (a.results?.nps?.respondents ?? 0))[0];

  const nps = sumNps(currentList.map((results) => results.nps));
  const previousNps = sumNps(previousList.map((results) => results.nps));

  return {
    ok: true,
    data: {
      period,
      totals: sumTotals(currentList.map((results) => results.responseRate)),
      previous: sumTotals(previousList.map((results) => results.responseRate)),
      ...(nps !== undefined ? { nps } : {}),
      ...(previousNps !== undefined ? { previousNps } : {}),
      timeline: mergeTimelines(
        currentList.map((results) => results.responseRate.timeline),
        windows.from,
        windows.to,
      ),
      surveys: performance,
      stateCounts,
      respondents: respondents.ok ? respondents.data.total : 0,
      ...(sdk.ok ? { sdk: sdk.data } : {}),
      sdkErrors: sdkErrors.ok ? sdkErrors.data.total : 0,
      ...(featuredSurvey !== undefined
        ? {
            featured: {
              id: featuredSurvey.survey.id,
              name: featuredSurvey.survey.name,
              attributes: featuredSurvey.results?.attributes ?? [],
            },
          }
        : {}),
    },
  };
}

function indexResults(surveys: readonly Survey[], results: readonly Result<SurveyResults>[]) {
  const map = new Map<string, SurveyResults>();
  surveys.forEach((survey, index) => {
    const result = results[index];
    if (result?.ok) {
      map.set(survey.id, result.data);
    }
  });
  return map;
}

export type SegmentRow = {
  value: string;
  displayed: number;
  completed: number;
  rate?: number;
  nps?: NpsTotals;
};

/** Até seis valores mais frequentes do atributo, mais o grupo de quem não enviou o atributo. */
const MAX_SEGMENTS = 6;

/**
 * Análise por segmento: a mesma pesquisa lida uma vez por valor do atributo, com o mesmo
 * período. Cada linha é o que o backend apura para aquele recorte.
 */
export async function getSegmentBreakdown(
  applicationId: string,
  surveyId: string,
  attribute: AttributeCatalog,
  period: OverviewPeriod,
  now: Date = new Date(),
): Promise<SegmentRow[]> {
  const { current } = periodWindows(period, now);
  const values = [...attribute.values].sort((a, b) => b.count - a.count).slice(0, MAX_SEGMENTS);
  const results = await Promise.all(
    values.map((entry) =>
      getSurveyResults(applicationId, surveyId, { ...current, attribute: attribute.name, attributeValue: entry.value }),
    ),
  );
  return values.flatMap((entry, index) => {
    const result = results[index];
    if (result === undefined || !result.ok) {
      return [];
    }
    const totals = sumTotals([result.data.responseRate]);
    const nps = sumNps([result.data.nps]);
    return [
      {
        value: entry.value,
        displayed: totals.displayed,
        completed: totals.completed,
        ...(totals.rate !== undefined ? { rate: totals.rate } : {}),
        ...(nps !== undefined ? { nps } : {}),
      },
    ];
  });
}
