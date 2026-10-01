import type { NpsSummary, ResponseRate, SurveyResults, TimelinePoint } from "@/features/results";

/**
 * Agregação da visão geral da aplicação a partir dos resultados de cada pesquisa.
 *
 * Tudo aqui é soma de contagens que o backend já apurou — nunca média de taxas: a taxa de
 * resposta da aplicação é concluídas ÷ exibidas sobre o total, para uma pesquisa pequena não
 * pesar como uma grande.
 */

export const OVERVIEW_PERIODS = ["7", "30", "90"] as const;
export type OverviewPeriod = (typeof OVERVIEW_PERIODS)[number];
export const DEFAULT_OVERVIEW_PERIOD: OverviewPeriod = "30";

export const OVERVIEW_PERIOD_LABELS: Record<OverviewPeriod, string> = {
  "7": "7 dias",
  "30": "30 dias",
  "90": "90 dias",
};

export function parseOverviewPeriod(raw: string | string[] | undefined): OverviewPeriod {
  const value = Array.isArray(raw) ? raw[0] : raw;
  return (OVERVIEW_PERIODS as readonly string[]).includes(value ?? "")
    ? (value as OverviewPeriod)
    : DEFAULT_OVERVIEW_PERIOD;
}

export type Totals = {
  displayed: number;
  completed: number;
  dismissed: number;
  abandoned: number;
  inProgress: number;
  /** Ausente sem exibição — nunca 0%. */
  rate?: number;
};

export type NpsTotals = {
  respondents: number;
  promoters: number;
  passives: number;
  detractors: number;
  /** Ausente sem resposta — NPS zero seria uma afirmação sem dado. */
  score?: number;
};

export const EMPTY_TOTALS: Totals = { displayed: 0, completed: 0, dismissed: 0, abandoned: 0, inProgress: 0 };

export function sumTotals(rates: readonly ResponseRate[]): Totals {
  const sum = rates.reduce(
    (acc, rate) => ({
      displayed: acc.displayed + rate.displayed,
      completed: acc.completed + rate.completed,
      dismissed: acc.dismissed + rate.dismissed,
      abandoned: acc.abandoned + rate.abandoned,
      inProgress: acc.inProgress + rate.inProgress,
    }),
    { ...EMPTY_TOTALS },
  );
  return sum.displayed > 0 ? { ...sum, rate: sum.completed / sum.displayed } : sum;
}

export function sumNps(summaries: readonly (NpsSummary | undefined)[]): NpsTotals | undefined {
  const present = summaries.filter((s): s is NpsSummary => s !== undefined);
  if (present.length === 0) {
    return undefined;
  }
  const totals = present.reduce(
    (acc, s) => ({
      respondents: acc.respondents + s.respondents,
      promoters: acc.promoters + s.promoters,
      passives: acc.passives + s.passives,
      detractors: acc.detractors + s.detractors,
    }),
    { respondents: 0, promoters: 0, passives: 0, detractors: 0 },
  );
  return totals.respondents > 0
    ? { ...totals, score: ((totals.promoters - totals.detractors) / totals.respondents) * 100 }
    : totals;
}

const DAY_MS = 24 * 60 * 60 * 1000;

/** Dia UTC `YYYY-MM-DD` de um instante. */
export function utcDay(instant: Date): string {
  return instant.toISOString().slice(0, 10);
}

/**
 * Soma as linhas do tempo das pesquisas dia a dia e preenche com zero os dias do período sem
 * exibição: o backend só lista dias com exibição, e zero ali é verdade, não ausência de dado.
 */
export function mergeTimelines(
  timelines: readonly (readonly TimelinePoint[])[],
  from: Date,
  to: Date,
): TimelinePoint[] {
  const byDay = new Map<string, TimelinePoint>();
  for (let t = Date.UTC(from.getUTCFullYear(), from.getUTCMonth(), from.getUTCDate()); t <= to.getTime(); t += DAY_MS) {
    const day = utcDay(new Date(t));
    byDay.set(day, { day, displayed: 0, completed: 0 });
  }
  for (const timeline of timelines) {
    for (const point of timeline) {
      const current = byDay.get(point.day);
      if (current === undefined) {
        continue;
      }
      current.displayed += point.displayed;
      current.completed += point.completed;
    }
  }
  return [...byDay.values()];
}

/** Variação relativa entre períodos. Sem base no período anterior, não há variação a mostrar. */
export function relativeChange(current: number, previous: number): number | undefined {
  return previous > 0 ? (current - previous) / previous : undefined;
}

/** Variação absoluta em pontos (percentuais ou de NPS). Qualquer lado ausente, ausente. */
export function pointChange(current: number | undefined, previous: number | undefined): number | undefined {
  return current === undefined || previous === undefined ? undefined : current - previous;
}

export type SurveyPerformance = {
  id: string;
  name: string;
  state: string;
  template?: string;
  totals: Totals;
  previousRate?: number;
  nps?: NpsTotals;
  timeline: TimelinePoint[];
};

export function surveyPerformance(
  survey: { id: string; name: string; state: string; templateKind?: string },
  current: SurveyResults | undefined,
  previous: SurveyResults | undefined,
): SurveyPerformance {
  const totals = current === undefined ? { ...EMPTY_TOTALS } : sumTotals([current.responseRate]);
  const previousTotals = previous === undefined ? undefined : sumTotals([previous.responseRate]);
  return {
    id: survey.id,
    name: survey.name,
    state: survey.state,
    ...(survey.templateKind !== undefined ? { template: survey.templateKind } : {}),
    totals,
    ...(previousTotals?.rate !== undefined ? { previousRate: previousTotals.rate } : {}),
    ...(current?.nps !== undefined ? { nps: sumNps([current.nps]) } : {}),
    timeline: current?.responseRate.timeline ?? [],
  };
}

/** Recorte do período atual e do anterior, de mesma duração, para as variações. */
export function periodWindows(period: OverviewPeriod, now: Date = new Date()) {
  const days = Number.parseInt(period, 10);
  const from = new Date(now.getTime() - days * DAY_MS);
  const previousFrom = new Date(now.getTime() - 2 * days * DAY_MS);
  return {
    current: { from: from.toISOString() },
    previous: { from: previousFrom.toISOString(), to: from.toISOString() },
    from,
    to: now,
  };
}
