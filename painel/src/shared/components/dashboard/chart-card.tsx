import type { ReactNode } from "react";

import { cn } from "@/lib/utils";

/** Moldura de gráfico: título, descrição, ações à direita e o corpo. */
export function ChartCard({
  title,
  description,
  actions,
  children,
  className,
  testId,
}: {
  title: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
  testId?: string;
}) {
  return (
    <section
      data-testid={testId}
      className={cn("flex min-w-0 flex-col gap-4 rounded-xl border border-border bg-card p-5 shadow-xs", className)}
    >
      <header className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex min-w-0 flex-col gap-0.5">
          <h3 className="text-[15px] leading-[22px] font-semibold">{title}</h3>
          {description ? <p className="text-[13px] leading-[18px] text-ink-muted">{description}</p> : null}
        </div>
        {actions ? <div className="flex items-center gap-2">{actions}</div> : null}
      </header>
      {children}
    </section>
  );
}
