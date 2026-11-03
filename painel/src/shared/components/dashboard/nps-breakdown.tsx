import { cn } from "@/lib/utils";

import { formatInt, formatNps, formatPercent } from "@/shared/lib/number-format";

const GROUPS = [
  { key: "promoters", label: "Promotores (9–10)", color: "bg-success-fill" },
  { key: "passives", label: "Neutros (7–8)", color: "bg-neutral-fill" },
  { key: "detractors", label: "Detratores (0–6)", color: "bg-danger-fill" },
] as const;

export type NpsGroups = {
  respondents: number;
  promoters: number;
  passives: number;
  detractors: number;
  score?: number;
};

/**
 * NPS como número-herói (sempre com sinal) e a barra de promotores, neutros e detratores.
 * Cores semânticas — não de série — e sempre com o rótulo da faixa.
 */
export function NpsBreakdown({
  nps,
  size = "lg",
  note,
  scoreTestId = "nps-breakdown-score",
  className,
}: {
  nps: NpsGroups | undefined;
  size?: "lg" | "sm";
  /** Substitui a frase padrão ao lado do número. */
  note?: string;
  scoreTestId?: string;
  className?: string;
}) {
  const respondents = nps?.respondents ?? 0;

  return (
    <div className={cn("flex flex-col gap-4", className)} data-testid="nps-breakdown">
      <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
        <span
          data-testid={scoreTestId}
          className={cn("font-semibold tracking-tight tabular-nums", size === "lg" ? "text-4xl" : "text-2xl")}
        >
          {formatNps(nps?.score)}
        </span>
        <span className="text-[13px] text-ink-muted">
          {note ??
            (respondents === 0
              ? "Ninguém respondeu a pergunta de NPS no período."
              : `sobre ${formatInt(respondents)} resposta${respondents === 1 ? "" : "s"}`)}
        </span>
      </div>
      {respondents > 0 && nps !== undefined ? (
        <>
          <div className="flex h-3 gap-0.5" role="img" aria-label="Promotores, neutros e detratores">
            {GROUPS.filter((group) => nps[group.key] > 0).map((group, index, list) => (
              <span
                key={group.key}
                className={cn(group.color, index === 0 && "rounded-l-sm", index === list.length - 1 && "rounded-r-sm")}
                style={{ flexGrow: nps[group.key], flexBasis: 0 }}
              />
            ))}
          </div>
          <ul className="flex flex-col gap-1.5 text-xs">
            {GROUPS.map((group) => (
              <li key={group.key} className="flex items-center gap-2">
                <span className={cn("size-2.5 rounded-[2px]", group.color)} aria-hidden />
                <span className="text-ink-secondary">{group.label}</span>
                <span className="ml-auto font-medium tabular-nums">{formatInt(nps[group.key])}</span>
                <span className="w-12 text-right text-ink-muted tabular-nums">
                  {formatPercent(nps[group.key] / respondents)}
                </span>
              </li>
            ))}
          </ul>
        </>
      ) : null}
    </div>
  );
}
