import { ArrowDownIcon, ArrowUpIcon } from "lucide-react";
import type { ReactNode } from "react";

import { cn } from "@/lib/utils";

import { formatDelta } from "@/shared/lib/number-format";

export type KpiDelta = {
  value: number;
  unit: "relative" | "points" | "nps";
  /** Sentido bom da métrica: menos abandono é bom, mais exibição é bom. */
  goodWhen: "up" | "down";
};

/**
 * O KPI do dashboard: um número por card, com a variação contra o período anterior de mesma
 * duração. A cor segue o sentido **bom** da métrica, e a seta segue o sentido do número —
 * então a cor nunca carrega o significado sozinha.
 */
export function KpiCard({
  label,
  value,
  delta,
  note,
  icon,
  testId,
  className,
}: {
  label: string;
  value: ReactNode;
  delta?: KpiDelta;
  note?: ReactNode;
  icon?: ReactNode;
  testId?: string;
  className?: string;
}) {
  const up = delta !== undefined && delta.value > 0;
  const flat = delta !== undefined && Math.abs(delta.value) < 1e-9;
  const good = delta !== undefined && (delta.goodWhen === "up" ? delta.value > 0 : delta.value < 0);

  return (
    <div
      data-testid={testId}
      className={cn(
        "flex min-w-0 flex-col gap-2 rounded-xl border border-border bg-card p-5 shadow-xs",
        className,
      )}
    >
      <div className="flex items-center justify-between gap-2">
        <span className="text-[11px] font-medium tracking-[0.06em] text-ink-muted uppercase">{label}</span>
        {icon ? <span className="text-ink-muted [&_svg]:size-4">{icon}</span> : null}
      </div>
      <span className="text-[28px] leading-8 font-semibold tracking-tight tabular-nums" data-testid={testId ? `${testId}-value` : undefined}>
        {value}
      </span>
      <div className="flex min-h-4 flex-wrap items-center gap-x-2 gap-y-1 text-xs">
        {delta !== undefined ? (
          <span
            className={cn(
              "inline-flex items-center gap-0.5 font-medium",
              flat ? "text-ink-muted" : good ? "text-success" : "text-danger",
            )}
          >
            {flat ? null : up ? <ArrowUpIcon aria-hidden className="size-3" /> : <ArrowDownIcon aria-hidden className="size-3" />}
            <span className="sr-only">{flat ? "estável" : up ? "subiu" : "caiu"}</span>
            {formatDelta(delta.value, delta.unit)}
          </span>
        ) : null}
        {note ? <span className="text-ink-muted">{note}</span> : null}
      </div>
    </div>
  );
}
