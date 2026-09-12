import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

import { formatCount, formatRate, SMALL_SAMPLE_NOTE } from "../lib/results-labels";
import type { ResponseRate } from "../schemas/results";

/**
 * As quatro contagens, a taxa e a **definição do cálculo ao lado dos números** (FR-05): sem a
 * definição visível, cada pessoa interpreta a taxa do seu jeito.
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
        <CardTitle className="flex flex-wrap items-center gap-3">
          Taxa de resposta
          <span data-testid="response-rate-value" className="text-2xl font-semibold">
            {formatRate(responseRate.rate)}
          </span>
          {smallSample && responseRate.displayed > 0 ? (
            <Badge variant="outline" data-testid="small-sample-badge">
              Amostra pequena
            </Badge>
          ) : null}
        </CardTitle>
        <p data-testid="response-rate-definition" className="text-sm text-muted-foreground">
          {responseRate.definition}
        </p>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <dl className="grid gap-4 sm:grid-cols-5">
          <Stat label="Exibidas" value={responseRate.displayed} testId="stat-displayed" />
          <Stat label="Concluídas" value={responseRate.completed} testId="stat-completed" />
          <Stat label="Dispensadas" value={responseRate.dismissed} testId="stat-dismissed" />
          <Stat label="Abandonadas" value={responseRate.abandoned} testId="stat-abandoned" />
          <Stat label="Em andamento" value={responseRate.inProgress} testId="stat-in-progress" />
        </dl>

        {smallSample && responseRate.displayed > 0 ? (
          <p className="text-sm text-muted-foreground">{SMALL_SAMPLE_NOTE}</p>
        ) : null}

        <Timeline responseRate={responseRate} />
      </CardContent>
    </Card>
  );
}

function Stat({ label, value, testId }: { label: string; value: number; testId: string }) {
  return (
    <div className="flex flex-col gap-1">
      <dt className="text-xs tracking-wide text-muted-foreground uppercase">{label}</dt>
      <dd data-testid={testId} className="text-lg font-medium">
        {formatCount(value)}
      </dd>
    </div>
  );
}

const dayFormatter = new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "2-digit" });

/** Evolução por dia de abertura: barra de exibidas com a fatia de concluídas por cima. */
function Timeline({ responseRate }: { responseRate: ResponseRate }) {
  if (responseRate.timeline.length === 0) {
    return null;
  }

  const peak = Math.max(...responseRate.timeline.map((point) => point.displayed), 1);

  return (
    <div className="flex flex-col gap-2">
      <p className="text-xs tracking-wide text-muted-foreground uppercase">
        Evolução por dia de abertura (UTC)
      </p>
      <ol data-testid="timeline" className="flex flex-col gap-1">
        {responseRate.timeline.map((point) => (
          <li key={point.day} data-testid="timeline-point" className="flex items-center gap-3 text-xs">
            <span className="w-12 shrink-0 text-muted-foreground">
              {dayFormatter.format(new Date(`${point.day}T00:00:00Z`))}
            </span>
            <span className="relative h-3 flex-1 overflow-hidden rounded bg-muted">
              <span
                className="absolute inset-y-0 left-0 bg-primary/30"
                style={{ width: `${(point.displayed / peak) * 100}%` }}
              />
              <span
                className="absolute inset-y-0 left-0 bg-primary"
                style={{ width: `${(point.completed / peak) * 100}%` }}
              />
            </span>
            <span className="w-28 shrink-0 text-muted-foreground">
              {formatCount(point.completed)} de {formatCount(point.displayed)}
            </span>
          </li>
        ))}
      </ol>
    </div>
  );
}
