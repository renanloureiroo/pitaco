import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

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
            rows={(aggregate.options ?? []).map((option) => ({
              key: option.value,
              label: option.label,
              count: option.count,
              share: option.share,
            }))}
          />
          {question.type === "multiple_choice" ? (
            <p className="text-xs text-muted-foreground">{MULTIPLE_CHOICE_SHARE_NOTE}</p>
          ) : null}
        </div>
      );
    case "numeric":
      return (
        <div className="flex flex-col gap-3">
          <p className="text-sm">
            Média{" "}
            <span data-testid="question-average" className="font-medium">
              {formatAverage(aggregate.average ?? 0)}
            </span>
          </p>
          <Bars rows={valueRows(aggregate.distribution ?? [])} />
        </div>
      );
    case "nps":
      return (
        <div className="flex flex-col gap-3">
          <dl className="grid gap-4 sm:grid-cols-4">
            <Group label="NPS" value={formatScore(aggregate.score ?? 0)} testId="nps-score" />
            <Group label="Promotores (9–10)" value={formatCount(aggregate.promoters ?? 0)} testId="nps-promoters" />
            <Group label="Neutros (7–8)" value={formatCount(aggregate.passives ?? 0)} testId="nps-passives" />
            <Group label="Detratores (0–6)" value={formatCount(aggregate.detractors ?? 0)} testId="nps-detractors" />
          </dl>
          <Bars rows={valueRows(aggregate.distribution ?? [])} />
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
      className="rounded-md border border-destructive/40 bg-destructive/5 p-3 text-sm text-destructive"
    >
      {incomparableMessage(comparability.versions)}
    </p>
  );
}

function valueRows(distribution: ValueShare[]) {
  return distribution.map((entry) => ({
    key: String(entry.value),
    label: String(entry.value),
    count: entry.count,
    share: entry.share,
  }));
}

function Group({ label, value, testId }: { label: string; value: string; testId: string }) {
  return (
    <div className="flex flex-col gap-1">
      <dt className="text-xs tracking-wide text-muted-foreground uppercase">{label}</dt>
      <dd data-testid={testId} className="text-lg font-medium">
        {value}
      </dd>
    </div>
  );
}

type BarRow = Pick<OptionShare, "count" | "share"> & { key: string; label: string };

function Bars({ rows }: { rows: BarRow[] }) {
  return (
    <ol className="flex flex-col gap-1.5">
      {rows.map((row) => (
        <li key={row.key} data-testid="aggregate-row" className="flex items-center gap-3 text-sm">
          <span className="w-40 shrink-0 truncate" title={row.label}>
            {row.label}
          </span>
          <span className="relative h-3 flex-1 overflow-hidden rounded bg-muted">
            <span
              className="absolute inset-y-0 left-0 bg-primary"
              style={{ width: `${Math.min(row.share, 1) * 100}%` }}
            />
          </span>
          <span data-testid="aggregate-count" className="w-32 shrink-0 text-right text-muted-foreground">
            {formatCount(row.count)} · {formatShare(row.share)}
          </span>
        </li>
      ))}
    </ol>
  );
}
