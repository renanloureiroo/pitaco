import { cn } from "@/lib/utils";

import { formatInt, formatPercent } from "@/shared/lib/number-format";

export type OutcomeCounts = {
  completed: number;
  dismissed: number;
  abandoned: number;
  inProgress: number;
};

const SEGMENTS = [
  { key: "completed", label: "Concluídas", color: "bg-chart-3", hint: "responderam até o fim" },
  { key: "dismissed", label: "Dispensadas", color: "bg-chart-1", hint: "fecharam sem responder" },
  { key: "abandoned", label: "Abandonadas", color: "bg-chart-2", hint: "pararam no meio ou sumiram por 30 min" },
  { key: "inProgress", label: "Em andamento", color: "bg-neutral-fill", hint: "abertas há menos de 30 min" },
] as const;

/**
 * Para onde vão as exibições: barra 100% empilhada com 2px de vão e a legenda com contagem e
 * proporção — identidade pelo rótulo, não só pela cor.
 */
export function OutcomeBreakdown({ counts, className }: { counts: OutcomeCounts; className?: string }) {
  const total = counts.completed + counts.dismissed + counts.abandoned + counts.inProgress;
  const visible = SEGMENTS.filter((segment) => counts[segment.key] > 0);

  return (
    <div className={cn("flex flex-col gap-4", className)} data-testid="outcome-breakdown">
      {total === 0 ? (
        <div className="h-3 rounded-sm bg-surface-sunken" />
      ) : (
        <div className="flex h-3 gap-0.5" role="img" aria-label="Distribuição dos desfechos das exibições">
          {visible.map((segment, index) => (
            <span
              key={segment.key}
              className={cn(
                segment.color,
                index === 0 && "rounded-l-sm",
                index === visible.length - 1 && "rounded-r-sm",
              )}
              style={{ flexGrow: counts[segment.key], flexBasis: 0 }}
            />
          ))}
        </div>
      )}
      <dl className="grid grid-cols-2 gap-x-6 gap-y-3">
        {SEGMENTS.map((segment) => (
          <div key={segment.key} className="flex flex-col gap-0.5" data-testid={`outcome-${segment.key}`}>
            <dt className="flex items-center gap-2 text-xs text-ink-secondary">
              <span className={cn("size-2.5 rounded-[2px]", segment.color)} aria-hidden />
              {segment.label}
            </dt>
            <dd className="flex items-baseline gap-2 pl-[18px]">
              <span className="text-lg font-semibold tabular-nums">{formatInt(counts[segment.key])}</span>
              <span className="text-xs text-ink-muted tabular-nums">
                {total > 0 ? formatPercent(counts[segment.key] / total) : "—"}
              </span>
            </dd>
          </div>
        ))}
      </dl>
    </div>
  );
}
