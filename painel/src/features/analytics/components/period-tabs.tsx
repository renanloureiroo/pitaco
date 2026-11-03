import Link from "next/link";

import { cn } from "@/lib/utils";

import { OVERVIEW_PERIOD_LABELS, OVERVIEW_PERIODS, type OverviewPeriod } from "../lib/aggregate";

/** Período da visão geral na URL (`?periodo=7|30|90`): compartilhável e com "voltar" funcionando. */
export function PeriodTabs({
  pathname,
  period,
  searchParams,
}: {
  pathname: string;
  period: OverviewPeriod;
  searchParams?: Record<string, string | undefined>;
}) {
  return (
    <nav aria-label="Período" className="inline-flex gap-0.5 rounded-md bg-surface-sunken p-[3px]">
      {OVERVIEW_PERIODS.map((value) => {
        const params = new URLSearchParams();
        for (const [key, current] of Object.entries(searchParams ?? {})) {
          if (current !== undefined && key !== "periodo") params.set(key, current);
        }
        params.set("periodo", value);
        const active = value === period;
        return (
          <Link
            key={value}
            href={`${pathname}?${params.toString()}`}
            aria-current={active ? "page" : undefined}
            className={cn(
              "rounded-[4px] px-3 py-1 text-[13px] transition-colors",
              active ? "bg-card font-medium text-foreground shadow-xs" : "text-ink-muted hover:text-foreground",
            )}
          >
            {OVERVIEW_PERIOD_LABELS[value]}
          </Link>
        );
      })}
    </nav>
  );
}
