import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { cn } from "@/lib/utils";

import {
  MULTIPLE_CHOICE_SHARE_NOTE,
  NOT_APPLICABLE_NOTE,
  NO_ANSWERS,
  comparableMessage,
  incomparableMessage,
  RESULT_TYPE_LABELS,
  formatAverage,
  formatCount,
  formatScore,
  formatShare,
} from "../lib/results-labels";
import type { OptionShare, QuestionAggregate, QuestionResult, ValueShare } from "../schemas/results";

/**
 * Cada tipo na forma que faz sentido para ele, sempre com **contagem absoluta e proporção**:
 * proporção sozinha esconde amostra pequena. Sem resposta é um estado próprio, não zero.
 */
export function QuestionResultCard({ question }: { question: QuestionResult }) {
  return (
    <Card data-testid="question-result" data-question-key={question.key}>
      <CardHeader>
        <CardTitle className="flex flex-wrap items-baseline gap-2">
          <span className="text-muted-foreground">{question.position}.</span>
          <span data-testid="question-statement">{question.statement}</span>
        </CardTitle>
        <p data-testid="question-counts" className="text-sm text-muted-foreground">
          {RESULT_TYPE_LABELS[question.type]} · {formatCount(question.answered)} respondida
          {question.answered === 1 ? "" : "s"} · {formatCount(question.skipped)} pulada
          {question.skipped === 1 ? "" : "s"}
          {question.notApplicable > 0
            ? ` · ${formatCount(question.notApplicable)} não aplicáve${question.notApplicable === 1 ? "l" : "is"}`
            : ""}
        </p>
        {question.notApplicable > 0 ? (
          <p data-testid="question-not-applicable-note" className="text-xs text-muted-foreground">
            {NOT_APPLICABLE_NOTE}
          </p>
        ) : null}
        <ComparabilityNote question={question} />
      </CardHeader>
      <CardContent>
        {question.aggregate === undefined ? (
          <p data-testid="question-no-answers" className="text-sm text-muted-foreground">
            {NO_ANSWERS}
          </p>
        ) : (
          <Aggregate question={question} aggregate={question.aggregate} />
        )}
      </CardContent>
    </Card>
  );
}

function Aggregate({ question, aggregate }: { question: QuestionResult; aggregate: QuestionAggregate }) {
  switch (aggregate.kind) {
    case "choice":
      return (
        <div className="flex flex-col gap-2">
          <Bars
            rows={(aggregate.options ?? []).map((option, index) => ({
              key: option.value,
              label: option.label,
              count: option.count,
              share: option.share,
              color: CHOICE_COLORS[index] ?? "bg-neutral-fill",
            }))}
          />
          {question.type === "multiple_choice" ? (
            <p className="text-xs text-muted-foreground">{MULTIPLE_CHOICE_SHARE_NOTE}</p>
          ) : null}
        </div>
      );
    case "numeric":
      return (
        <div className="flex flex-col gap-4">
          <div className="flex items-baseline gap-2">
            <span data-testid="question-average" className="text-3xl font-semibold tracking-tight tabular-nums">
              {formatAverage(aggregate.average ?? 0)}
            </span>
            <span className="text-sm text-ink-muted">de média</span>
          </div>
          <Histogram distribution={aggregate.distribution ?? []} colorOf={() => "bg-chart-1"} />
        </div>
      );
    case "nps":
      return (
        <div className="flex flex-col gap-4">
          <dl className="grid grid-cols-2 gap-4 sm:grid-cols-4">
            <Group label="NPS" value={formatSignedScore(aggregate.score ?? 0)} testId="nps-score" emphasis />
            <Group label="Promotores (9–10)" value={formatCount(aggregate.promoters ?? 0)} testId="nps-promoters" dot="bg-success-fill" />
            <Group label="Neutros (7–8)" value={formatCount(aggregate.passives ?? 0)} testId="nps-passives" dot="bg-neutral-fill" />
            <Group label="Detratores (0–6)" value={formatCount(aggregate.detractors ?? 0)} testId="nps-detractors" dot="bg-danger-fill" />
          </dl>
          <Histogram
            distribution={aggregate.distribution ?? []}
            fill={{ min: 0, max: 10 }}
            colorOf={(value) => (value >= 9 ? "bg-success-fill" : value >= 7 ? "bg-neutral-fill" : "bg-danger-fill")}
          />
        </div>
      );
    case "text":
      return (
        <p className="text-sm text-muted-foreground">
          As respostas de texto estão listadas na seção de respostas abertas, abaixo.
        </p>
      );
  }
}

/** Cor por posição da opção: a mesma opção tem a mesma cor em todo recorte. */
const CHOICE_COLORS = [
  "bg-chart-1",
  "bg-chart-2",
  "bg-chart-3",
  "bg-chart-4",
  "bg-chart-5",
  "bg-chart-6",
  "bg-chart-7",
  "bg-chart-8",
] as const;

function formatSignedScore(score: number): string {
  return score > 0 ? `+${formatScore(score)}` : formatScore(score);
}

/**
 * Distribuição de notas em colunas, com a contagem em cima e o valor embaixo. Com `fill`, os
 * valores sem resposta da faixa aparecem como coluna vazia — zero é dado, não buraco.
 */
function Histogram({
  distribution,
  colorOf,
  fill,
}: {
  distribution: ValueShare[];
  colorOf: (value: number) => string;
  fill?: { min: number; max: number };
}) {
  const byValue = new Map(distribution.map((entry) => [entry.value, entry]));
  const values =
    fill !== undefined
      ? Array.from({ length: fill.max - fill.min + 1 }, (_, index) => fill.min + index)
      : distribution.map((entry) => entry.value);
  const peak = Math.max(...distribution.map((entry) => entry.share), 0.0001);

  return (
    <ol className="flex h-40 items-end gap-1.5" aria-label="Distribuição das respostas">
      {values.map((value) => {
        const entry = byValue.get(value) ?? { value, count: 0, share: 0 };
        return (
          <li
            key={value}
            data-testid="aggregate-row"
            className="group flex h-full min-w-0 flex-1 flex-col items-center justify-end gap-1"
            title={`${value}: ${formatCount(entry.count)} · ${formatShare(entry.share)}`}
          >
            <span data-testid="aggregate-count" className="text-[11px] text-ink-muted tabular-nums">
              {formatCount(entry.count)}
              <span className="sr-only"> · {formatShare(entry.share)}</span>
            </span>
            <span
              className={cn("w-full max-w-12 rounded-t-sm transition-opacity group-hover:opacity-80", colorOf(value))}
              style={{ height: `${Math.max((entry.share / peak) * 100, entry.count > 0 ? 3 : 0)}%` }}
            />
            <span className="border-t border-border-strong pt-1 text-xs font-medium tabular-nums">{value}</span>
          </li>
        );
      })}
    </ol>
  );
}

/**
 * A marca vale por pergunta: as que não mudaram somam com segurança, e a tela diz isso; só as
 * que mudaram carregam o aviso. Uma versão só não tem soma a desconfiar.
 */
function ComparabilityNote({ question }: { question: QuestionResult }) {
  const comparability = question.comparability;
  if (comparability === undefined || comparability.versions.length < 2) {
    return null;
  }

  return comparability.comparable ? (
    <p data-testid="comparability-safe" className="text-xs text-muted-foreground">
      {comparableMessage(comparability.versions)}
    </p>
  ) : (
    <p
      data-testid="comparability-warning"
      role="note"
      className="rounded-md bg-danger-soft p-3 text-sm text-danger"
    >
      {incomparableMessage(comparability.versions)}
    </p>
  );
}

function Group({
  label,
  value,
  testId,
  dot,
  emphasis,
}: {
  label: string;
  value: string;
  testId: string;
  dot?: string;
  emphasis?: boolean;
}) {
  return (
    <div className="flex flex-col gap-1">
      <dt className="flex items-center gap-1.5 text-[11px] font-medium tracking-[0.06em] text-ink-muted uppercase">
        {dot ? <span className={cn("size-2 rounded-[2px]", dot)} aria-hidden /> : null}
        {label}
      </dt>
      <dd data-testid={testId} className={cn("font-semibold tabular-nums", emphasis ? "text-3xl tracking-tight" : "text-lg")}>
        {value}
      </dd>
    </div>
  );
}

type BarRow = Pick<OptionShare, "count" | "share"> & { key: string; label: string; color: string };

function Bars({ rows }: { rows: BarRow[] }) {
  return (
    <ol className="flex flex-col gap-2.5">
      {rows.map((row) => (
        <li key={row.key} data-testid="aggregate-row" className="flex flex-col gap-1 text-sm sm:flex-row sm:items-center sm:gap-3">
          <span className="flex items-center gap-2 sm:w-48 sm:shrink-0" title={row.label}>
            <span className={cn("size-2.5 shrink-0 rounded-[2px]", row.color)} aria-hidden />
            <span className="truncate">{row.label}</span>
          </span>
          <span className="flex flex-1 items-center gap-3">
            <span className="relative h-2.5 flex-1 overflow-hidden rounded-sm bg-surface-sunken">
              <span
                className={cn("absolute inset-y-0 left-0 rounded-sm", row.color)}
                style={{ width: `${Math.min(row.share, 1) * 100}%` }}
              />
            </span>
            <span data-testid="aggregate-count" className="w-28 shrink-0 text-right text-ink-muted tabular-nums">
              {formatCount(row.count)} · {formatShare(row.share)}
            </span>
          </span>
        </li>
      ))}
    </ol>
  );
}
