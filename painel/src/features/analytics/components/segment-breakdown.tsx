import { cn } from "@/lib/utils";

import type { SegmentRow } from "../api/overview";
import { formatInt, formatNps, formatPercent } from "@/shared/lib";

/**
 * NPS e taxa de resposta por valor de um atributo. O NPS vai de −100 a +100 com o zero no meio
 * (barra divergente: verde à direita, vermelho à esquerda); a taxa é uma barra simples.
 */
export function SegmentBreakdown({ rows, attribute }: { rows: SegmentRow[]; attribute: string }) {
  if (rows.length === 0) {
    return <p className="text-sm text-ink-muted">Nenhum valor de “{attribute}” no período.</p>;
  }
  const hasNps = rows.some((row) => row.nps?.score !== undefined);

  return (
    <div className="flex flex-col gap-1" data-testid="segment-breakdown">
      <div className="grid grid-cols-[minmax(80px,140px)_1fr_minmax(64px,auto)] items-center gap-x-4 pb-2 text-[11px] font-medium tracking-[0.06em] text-ink-muted uppercase sm:grid-cols-[minmax(80px,140px)_1fr_1fr]">
        <span>{attribute}</span>
        {hasNps ? <span className="text-center">NPS</span> : null}
        <span className={cn(!hasNps && "col-span-2")}>Taxa de resposta</span>
      </div>
      {rows.map((row) => {
        const score = row.nps?.score;
        return (
          <div
            key={row.value}
            data-testid="segment-row"
            className="grid grid-cols-[minmax(80px,140px)_1fr_minmax(64px,auto)] items-center gap-x-4 border-t border-border py-2.5 text-sm sm:grid-cols-[minmax(80px,140px)_1fr_1fr]"
          >
            <span className="flex flex-col">
              <span className="truncate font-medium" title={row.value}>
                {row.value}
              </span>
              <span className="text-xs text-ink-muted">{formatInt(row.displayed)} exibições</span>
            </span>
            {hasNps ? (
              <div className="flex items-center gap-2">
                <div className="relative h-2.5 flex-1">
                  <span className="absolute inset-y-0 left-1/2 w-px bg-border-strong" aria-hidden />
                  {score !== undefined ? (
                    <span
                      className={cn(
                        "absolute inset-y-0",
                        score >= 0 ? "left-1/2 rounded-r-sm bg-success-fill" : "right-1/2 rounded-l-sm bg-danger-fill",
                      )}
                      style={{ width: `${Math.min(Math.abs(score), 100) / 2}%` }}
                    />
                  ) : null}
                </div>
                <span className={cn("w-10 text-right font-medium tabular-nums", score === undefined ? "text-ink-muted" : score >= 0 ? "text-success" : "text-danger")}>
                  {formatNps(score)}
                </span>
              </div>
            ) : null}
            <div className={cn("flex items-center gap-2", !hasNps && "col-span-2")}>
              <div className="hidden h-2.5 flex-1 overflow-hidden rounded-sm bg-surface-sunken sm:block">
                <span className="block h-full rounded-sm bg-chart-1" style={{ width: `${(row.rate ?? 0) * 100}%` }} />
              </div>
              <span className="w-12 text-right tabular-nums">{formatPercent(row.rate)}</span>
            </div>
          </div>
        );
      })}
    </div>
  );
}
