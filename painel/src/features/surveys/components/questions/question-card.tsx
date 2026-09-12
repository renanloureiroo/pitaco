import type { ReactNode } from "react";

import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";

import { QUESTION_TYPE_LABELS } from "../../lib/survey-labels";
import type { Question } from "../../schemas/question";

/** O cartão de uma pergunta, o mesmo na lista fixa e na arrastável. */
export function QuestionCard({
  question,
  index,
  handle,
  actions,
  conditionSummary,
}: {
  question: Question;
  index: number;
  /** Alça de arrastar, quando a lista é reordenável. */
  handle?: ReactNode;
  actions?: ReactNode;
  /** Resumo legível da condição de exibição, quando a pergunta tem uma. */
  conditionSummary?: string;
}) {
  const labels = [question.range?.minLabel, question.range?.maxLabel].filter(
    (label): label is string => label !== undefined,
  );

  return (
    <Card>
      <CardContent className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex min-w-0 items-start gap-2">
          {handle}
          <div className="flex min-w-0 flex-col gap-2">
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-xs text-muted-foreground">{index + 1}.</span>
              <span className="font-medium">{question.statement}</span>
              {question.required ? <Badge variant="outline">Obrigatória</Badge> : null}
            </div>

            <div className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
              <Badge variant="secondary">{QUESTION_TYPE_LABELS[question.type]}</Badge>
              {question.range !== undefined ? (
                <span>
                  faixa de {question.range.min} a {question.range.max}
                  {labels.length > 0 ? ` (${labels.join(" … ")})` : ""}
                </span>
              ) : null}
            </div>

            {conditionSummary !== undefined ? (
              <p data-testid="question-condition" className="text-sm text-muted-foreground">
                {conditionSummary}
              </p>
            ) : null}

            {question.options !== undefined && question.options.length > 0 ? (
              <ul className="flex flex-wrap gap-2">
                {question.options.map((option) => (
                  <li
                    key={option.value}
                    className="rounded-md bg-muted px-2 py-1 text-xs text-muted-foreground"
                  >
                    {option.label}
                  </li>
                ))}
              </ul>
            ) : null}
          </div>
        </div>

        {actions !== undefined ? (
          <div className="flex shrink-0 items-center gap-1">{actions}</div>
        ) : null}
      </CardContent>
    </Card>
  );
}
