import Link from "next/link";
import { ArrowRightIcon } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { ChartCard } from "@/shared/components";
import { formatDuration } from "@/shared/lib";
import { cn } from "@/lib/utils";

import { formatCount, formatRate } from "../lib/results-labels";
import { changeRateOf, validationBlocksOf, type SurveyBehavior } from "../schemas/behavior";

/**
 * Leitura de comportamento: o que acontece dentro da pesquisa, pergunta a pergunta, a partir
 * dos eventos de interação do SDK. Todo número vem do backend com a definição em
 * `definitions`, que a tela mostra junto.
 */

const VIA_LABELS: Record<string, string> = {
  close_button: "Botão fechar",
  swipe: "Arrastar para baixo",
  backdrop: "Toque fora",
  hardware_back: "Voltar do Android",
  navigation: "Navegação do app",
  programmatic: "Pelo app (código)",
};

export const BEHAVIOR_METRIC_LABELS: Record<string, string> = {
  instrumented: "Exibições instrumentadas",
  viewed: "Vista",
  answered: "Respondida",
  skipped: "Pulada",
  abandoned: "Abandonada nela",
  activeTime: "Tempo ativo",
  revisitRate: "Taxa de volta",
  answerChangeRate: "Taxa de troca de resposta",
  validationBlocks: "Bloqueios de validação",
  dismissalVia: "Dispensa por via",
};

function share(part: number, whole: number): number | undefined {
  return whole > 0 ? part / whole : undefined;
}

/**
 * Funil por pergunta: de quem viu, quantos responderam, pularam ou pararam ali. A barra de
 * cada pergunta tem a largura de quem a viu sobre as exibições instrumentadas — a queda entre
 * linhas é a perda ao longo da pesquisa.
 */
export function QuestionFunnel({ behavior }: { behavior: SurveyBehavior }) {
  const base = behavior.instrumented;

  return (
    <ChartCard
      testId="behavior-funnel"
      title="Funil por pergunta"
      description={`Sobre ${formatCount(base)} exibições instrumentadas — onde as pessoas respondem, pulam e desistem. Perguntas com condição só aparecem para parte do público.`}
      actions={
        <ul className="flex flex-wrap gap-x-3 gap-y-1 text-xs text-ink-secondary">
          <li className="flex items-center gap-1.5"><span className="size-2.5 rounded-[2px] bg-chart-3" />Respondida</li>
          <li className="flex items-center gap-1.5"><span className="size-2.5 rounded-[2px] bg-chart-1" />Pulada</li>
          <li className="flex items-center gap-1.5"><span className="size-2.5 rounded-[2px] bg-chart-2" />Parou aqui</li>
        </ul>
      }
    >
      <ol className="flex flex-col gap-4">
        {behavior.questions.map((question, index) => {
          const reach = share(question.viewed, base) ?? 0;
          const previous = index > 0 ? behavior.questions[index - 1] : undefined;
          const lost = previous !== undefined ? previous.viewed - question.viewed : undefined;
          return (
            <li key={question.key} data-testid="funnel-row" className="flex flex-col gap-1.5">
              <div className="flex items-baseline justify-between gap-3 text-sm">
                <span className="min-w-0 truncate" title={question.statement}>
                  <span className="text-ink-muted tabular-nums">{question.position}.</span> {question.statement}
                </span>
                <span className="shrink-0 text-xs text-ink-muted tabular-nums">
                  {formatCount(question.viewed)} viram · {formatRate(share(question.viewed, base))}
                  {lost !== undefined && lost > 0 ? (
                    <span className="ml-2 text-danger">−{formatCount(lost)}</span>
                  ) : null}
                </span>
              </div>
              <div className="h-3 rounded-sm bg-surface-sunken">
                <div className="flex h-full gap-0.5" style={{ width: `${reach * 100}%` }}>
                  {[
                    { value: question.answered, color: "bg-chart-3" },
                    { value: question.skipped, color: "bg-chart-1" },
                    { value: question.abandoned, color: "bg-chart-2" },
                  ]
                    .filter((part) => part.value > 0)
                    .map((part, partIndex, list) => (
                      <span
                        key={part.color}
                        className={cn(part.color, partIndex === 0 && "rounded-l-sm", partIndex === list.length - 1 && "rounded-r-sm")}
                        style={{ flexGrow: part.value, flexBasis: 0 }}
                        title={`${formatCount(part.value)}`}
                      />
                    ))}
                </div>
              </div>
              <div className="flex flex-wrap gap-x-4 text-xs text-ink-muted tabular-nums">
                <span>{formatCount(question.answered)} respondidas</span>
                <span>{formatCount(question.skipped)} puladas</span>
                <span className={cn(question.abandoned > 0 && "text-danger")}>{formatCount(question.abandoned)} pararam aqui</span>
              </div>
            </li>
          );
        })}
      </ol>
    </ChartCard>
  );
}

/** Tempo ativo por pergunta: mediana em barra e o p90 como marca, na mesma escala. */
export function QuestionTimeChart({ behavior }: { behavior: SurveyBehavior }) {
  const peak = Math.max(...behavior.questions.map((q) => q.activeTime?.p90Ms ?? q.activeTime?.medianMs ?? 0), 1);

  return (
    <ChartCard
      testId="behavior-time"
      title="Tempo ativo por pergunta"
      description="Mediana (barra) e percentil 90 (marca), sem o tempo com o app em segundo plano"
    >
      <ol className="flex flex-col gap-3">
        {behavior.questions.map((question) => {
          const median = question.activeTime?.medianMs;
          const p90 = question.activeTime?.p90Ms;
          return (
            <li key={question.key} className="grid grid-cols-[2rem_1fr_auto] items-center gap-3 text-sm">
              <span className="text-ink-muted tabular-nums">{question.position}.</span>
              <span className="relative h-2.5 rounded-sm bg-surface-sunken" title={question.statement}>
                {median !== undefined ? (
                  <span className="absolute inset-y-0 left-0 rounded-sm bg-chart-1" style={{ width: `${(median / peak) * 100}%` }} />
                ) : null}
                {p90 !== undefined ? (
                  <span
                    className="absolute -inset-y-1 w-0.5 rounded-full bg-ink-secondary"
                    style={{ left: `calc(${(p90 / peak) * 100}% - 1px)` }}
                    aria-hidden
                  />
                ) : null}
              </span>
              <span className="w-28 text-right text-xs text-ink-muted tabular-nums">
                <span className="font-medium text-foreground">{formatDuration(median)}</span> · p90 {formatDuration(p90)}
              </span>
            </li>
          );
        })}
      </ol>
    </ChartCard>
  );
}

/** Como as pessoas fecham a pesquisa sem concluir: a via da última dispensa de cada exibição. */
export function DismissalChart({ behavior }: { behavior: SurveyBehavior }) {
  const vias = [...behavior.dismissals.byVia].sort((a, b) => b.count - a.count);
  const peak = Math.max(...vias.map((via) => via.count), 1);

  return (
    <ChartCard
      testId="behavior-dismissals"
      title="Como dispensam"
      description={`${formatCount(behavior.dismissals.total)} dispensas instrumentadas`}
    >
      <ol className="flex flex-col gap-2.5">
        {vias.map((via) => (
          <li key={via.via} className="grid grid-cols-[minmax(0,9rem)_1fr_auto] items-center gap-3 text-sm">
            <span className="truncate">{VIA_LABELS[via.via] ?? via.via}</span>
            <span className="h-2.5 rounded-sm bg-surface-sunken">
              <span className="block h-full rounded-sm bg-chart-2" style={{ width: `${(via.count / peak) * 100}%` }} />
            </span>
            <span className="w-24 text-right text-xs text-ink-muted tabular-nums">
              {formatCount(via.count)} · {formatRate(via.share)}
            </span>
          </li>
        ))}
        {behavior.dismissals.unspecified > 0 ? (
          <li className="text-xs text-ink-muted">{formatCount(behavior.dismissals.unspecified)} sem via informada</li>
        ) : null}
      </ol>
    </ChartCard>
  );
}

/** Atrito por pergunta: volta, troca de resposta e bloqueio de validação. */
export function FrictionTable({ behavior }: { behavior: SurveyBehavior }) {
  return (
    <ChartCard
      testId="behavior-friction"
      title="Sinais de atrito"
      description="Perguntas confusas fazem as pessoas voltarem, trocarem a resposta ou baterem na validação"
    >
      <div className="overflow-x-auto">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Pergunta</TableHead>
              <TableHead className="text-right">Taxa de volta</TableHead>
              <TableHead className="text-right">Taxa de troca de resposta</TableHead>
              <TableHead className="text-right">Bloqueios de validação</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {behavior.questions.map((question) => {
              const changeRate = changeRateOf(question);
              const blocks = validationBlocksOf(question);
              return (
                <TableRow key={question.key}>
                  <TableCell className="max-w-sm truncate" title={question.statement}>
                    <span className="text-ink-muted">{question.position}.</span> {question.statement}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">{formatRate(question.revisitRate)}</TableCell>
                  <TableCell className={cn("text-right tabular-nums", (changeRate ?? 0) >= 0.1 && "font-medium text-warning")}>
                    {formatRate(changeRate)}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">{formatCount(blocks)}</TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </div>
    </ChartCard>
  );
}

/** As definições que o backend manda, por extenso: a régua de cada número desta tela. */
export function BehaviorDefinitions({ behavior }: { behavior: SurveyBehavior }) {
  return (
    <details className="rounded-xl border border-border bg-card p-5 text-sm" data-testid="behavior-definitions">
      <summary className="cursor-pointer font-medium">Como cada número é calculado</summary>
      <dl className="mt-4 grid gap-3 sm:grid-cols-2">
        {behavior.definitions.map((definition) => (
          <div key={definition.metric} className="flex flex-col gap-0.5">
            <dt className="text-xs font-medium">{BEHAVIOR_METRIC_LABELS[definition.metric] ?? definition.metric}</dt>
            <dd className="text-xs text-ink-muted">{definition.definition}</dd>
          </div>
        ))}
      </dl>
    </details>
  );
}

/**
 * Resumo de comportamento na tela de resultados: os três sinais mais úteis e o caminho para a
 * leitura completa.
 */
export function BehaviorSummary({ behavior, href }: { behavior: SurveyBehavior; href: string }) {
  if (!behavior.everPublished) {
    return null;
  }
  const worst = [...behavior.questions].sort((a, b) => b.abandoned - a.abandoned)[0];
  const rates = behavior.questions.map(changeRateOf).filter((rate): rate is number => rate !== undefined);
  const changeRate = rates.length > 0 ? rates.reduce((sum, rate) => sum + rate, 0) / rates.length : undefined;
  const medianTotal = behavior.questions.reduce((sum, question) => sum + (question.activeTime?.medianMs ?? 0), 0);

  return (
    <ChartCard
      testId="behavior-card"
      title="Comportamento"
      description={`Interação dentro da pesquisa, a partir de ${formatCount(behavior.instrumented)} exibições instrumentadas`}
      actions={
        <Button asChild variant="outline" size="sm">
          <Link href={href}>
            Ver comportamento
            <ArrowRightIcon aria-hidden />
          </Link>
        </Button>
      }
    >
      <dl className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        <div className="flex min-w-0 flex-col gap-1 rounded-lg bg-surface-raised px-3 py-2.5">
          <dt className="text-xs text-ink-muted">Onde mais desistem</dt>
          <dd className="truncate text-sm font-medium" title={worst?.statement}>
            {worst === undefined || worst.abandoned === 0 ? "—" : `${worst.position}. ${worst.statement}`}
          </dd>
        </div>
        <div className="flex min-w-0 flex-col gap-1 rounded-lg bg-surface-raised px-3 py-2.5">
          <dt className="text-xs text-ink-muted">Tempo ativo (soma das medianas)</dt>
          <dd className="text-lg font-semibold tabular-nums">{formatDuration(medianTotal > 0 ? medianTotal : undefined)}</dd>
        </div>
        <div className="flex min-w-0 flex-col gap-1 rounded-lg bg-surface-raised px-3 py-2.5">
          <dt className="text-xs text-ink-muted">Taxa de troca de resposta (média)</dt>
          <dd className="text-lg font-semibold tabular-nums">{formatRate(changeRate)}</dd>
        </div>
      </dl>
    </ChartCard>
  );
}
