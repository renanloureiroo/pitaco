import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { OutcomeBreakdown, TrendChart } from "@/shared/components";
import { cn } from "@/lib/utils";

import { formatCount, formatRate, SMALL_SAMPLE_NOTE } from "../lib/results-labels";
import type { ResponseRate } from "../schemas/results";

/**
 * As contagens, a taxa e a **definição do cálculo ao lado dos números** (FR-05): sem a
 * definição visível, cada pessoa interpreta a taxa do seu jeito. A evolução diária vem em
 * gráfico e também em lista (leitor de tela e quem prefere o número exato).
 */
export function ResponseRateCard({
  responseRate,
  smallSample,
}: {
  responseRate: ResponseRate;
  smallSample: boolean;
}) {
  return (
    <Card data-testid="response-rate">
      <CardHeader>
        <CardTitle className="flex flex-wrap items-center gap-3 text-[15px] font-semibold">
          Taxa de resposta
          {smallSample && responseRate.displayed > 0 ? (
            <Badge variant="warning" data-testid="small-sample-badge">
              Amostra pequena
            </Badge>
          ) : null}
        </CardTitle>
        <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
          <span data-testid="response-rate-value" className="text-4xl font-semibold tracking-tight tabular-nums">
            {formatRate(responseRate.rate)}
          </span>
          <p data-testid="response-rate-definition" className="text-[13px] text-ink-muted">
            {responseRate.definition}
          </p>
        </div>
      </CardHeader>
      <CardContent className="flex flex-col gap-6">
        <dl className="grid grid-cols-2 gap-3 sm:grid-cols-5">
          <Stat label="Exibidas" value={responseRate.displayed} testId="stat-displayed" />
          <Stat label="Concluídas" value={responseRate.completed} testId="stat-completed" dot="bg-chart-3" />
          <Stat label="Dispensadas" value={responseRate.dismissed} testId="stat-dismissed" dot="bg-chart-1" />
          <Stat label="Abandonadas" value={responseRate.abandoned} testId="stat-abandoned" dot="bg-chart-2" />
          <Stat label="Em andamento" value={responseRate.inProgress} testId="stat-in-progress" dot="bg-neutral-fill" />
        </dl>

        {smallSample && responseRate.displayed > 0 ? (
          <p className="rounded-md bg-warning-soft px-3 py-2 text-sm text-warning">{SMALL_SAMPLE_NOTE}</p>
        ) : null}

        {responseRate.displayed > 0 ? <OutcomeBreakdown counts={responseRate} className="[&_dl]:hidden" /> : null}

        <Timeline responseRate={responseRate} />
      </CardContent>
    </Card>
  );
}

function Stat({ label, value, testId, dot }: { label: string; value: number; testId: string; dot?: string }) {
  return (
    <div className="flex flex-col gap-1 rounded-lg bg-surface-raised px-3 py-2.5">
      <dt className="flex items-center gap-1.5 text-[11px] font-medium tracking-[0.06em] text-ink-muted uppercase">
        {dot ? <span className={cn("size-2 rounded-[2px]", dot)} aria-hidden /> : null}
        {label}
      </dt>
      <dd data-testid={testId} className="text-xl font-semibold tabular-nums">
        {formatCount(value)}
      </dd>
    </div>
  );
}

const dayFormatter = new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "2-digit", timeZone: "UTC" });

/** Evolução por dia de abertura: gráfico de área e, por baixo, a mesma série em lista. */
function Timeline({ responseRate }: { responseRate: ResponseRate }) {
  if (responseRate.timeline.length === 0) {
    return null;
  }

  return (
    <div className="flex flex-col gap-2">
      <p className="text-[11px] font-medium tracking-[0.06em] text-ink-muted uppercase">
        Evolução por dia de abertura (UTC)
      </p>
      {responseRate.timeline.length > 1 ? <TrendChart points={responseRate.timeline} /> : null}
      <details className="group text-xs">
        <summary className="cursor-pointer text-ink-muted hover:text-foreground">Ver os números por dia</summary>
        <ol data-testid="timeline" className="mt-2 grid grid-cols-2 gap-x-6 gap-y-1 sm:grid-cols-3 lg:grid-cols-4">
          {responseRate.timeline.map((point) => (
            <li key={point.day} data-testid="timeline-point" className="flex justify-between gap-3 tabular-nums">
              <span className="text-ink-muted">{dayFormatter.format(new Date(`${point.day}T00:00:00Z`))}</span>
              <span>
                {formatCount(point.completed)} de {formatCount(point.displayed)}
              </span>
            </li>
          ))}
        </ol>
      </details>
    </div>
  );
}
