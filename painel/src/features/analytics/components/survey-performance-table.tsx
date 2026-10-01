import Link from "next/link";
import { ArrowDownIcon, ArrowUpIcon } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { cn } from "@/lib/utils";

import type { SurveyPerformance } from "../lib/aggregate";
import { formatInt, formatNps, formatPercent } from "@/shared/lib";
import { Sparkline } from "./sparkline";

const STATE_BADGE: Record<string, { label: string; variant: "success" | "warning" | "secondary" | "destructive" | "info" }> = {
  active: { label: "No ar", variant: "success" },
  scheduled: { label: "Agendada", variant: "info" },
  paused: { label: "Pausada", variant: "warning" },
  ended: { label: "Encerrada", variant: "destructive" },
  draft: { label: "Rascunho", variant: "secondary" },
};

/** Desempenho de cada pesquisa no período, da mais exibida para a menos. */
export function SurveyPerformanceTable({
  applicationId,
  surveys,
}: {
  applicationId: string;
  surveys: SurveyPerformance[];
}) {
  return (
    <div className="overflow-x-auto" data-testid="survey-performance">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Pesquisa</TableHead>
            <TableHead>Estado</TableHead>
            <TableHead>Tendência</TableHead>
            <TableHead className="text-right">Exibições</TableHead>
            <TableHead className="text-right">Concluídas</TableHead>
            <TableHead className="text-right">Taxa</TableHead>
            <TableHead className="text-right">NPS</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {surveys.map((survey) => {
            const badge = STATE_BADGE[survey.state] ?? { label: survey.state, variant: "secondary" as const };
            const change =
              survey.totals.rate !== undefined && survey.previousRate !== undefined
                ? survey.totals.rate - survey.previousRate
                : undefined;
            return (
              <TableRow key={survey.id} data-testid="survey-performance-row">
                <TableCell className="max-w-72">
                  <Link
                    href={`/aplicacoes/${applicationId}/pesquisas/${survey.id}/resultados`}
                    className="flex flex-col font-medium hover:underline"
                  >
                    <span className="truncate">{survey.name}</span>
                    {survey.template !== undefined ? (
                      <span className="text-xs font-normal text-ink-muted uppercase">{survey.template}</span>
                    ) : null}
                  </Link>
                </TableCell>
                <TableCell>
                  <Badge variant={badge.variant}>
                    <span className="size-1.5 rounded-full bg-current" aria-hidden />
                    {badge.label}
                  </Badge>
                </TableCell>
                <TableCell>
                  <Sparkline points={survey.timeline} />
                </TableCell>
                <TableCell className="text-right tabular-nums">{formatInt(survey.totals.displayed)}</TableCell>
                <TableCell className="text-right tabular-nums">{formatInt(survey.totals.completed)}</TableCell>
                <TableCell className="text-right tabular-nums">
                  <span className="inline-flex items-center justify-end gap-1.5">
                    {formatPercent(survey.totals.rate)}
                    {change !== undefined && Math.abs(change) >= 0.005 ? (
                      <span className={cn("inline-flex items-center text-xs", change > 0 ? "text-success" : "text-danger")}>
                        {change > 0 ? <ArrowUpIcon aria-hidden className="size-3" /> : <ArrowDownIcon aria-hidden className="size-3" />}
                        <span className="sr-only">{change > 0 ? "subiu" : "caiu"}</span>
                        {Math.abs(Math.round(change * 1000) / 10).toLocaleString("pt-BR")}
                      </span>
                    ) : null}
                  </span>
                </TableCell>
                <TableCell className="text-right tabular-nums">{formatNps(survey.nps?.score)}</TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </div>
  );
}
