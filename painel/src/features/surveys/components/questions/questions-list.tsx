import type { ReactNode } from "react";

import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";

import { QUESTION_TYPE_LABELS } from "../../lib/survey-labels";
import type { Question } from "../../schemas/question";

/**
 * As perguntas são exibidas na ordem de `position`, que é a verdade do backend — a ordem em
 * que o array chegou não é garantia.
 */
export function QuestionsList({
  questions,
  actionsFor,
}: {
  questions: Question[];
  actionsFor?: (question: Question) => ReactNode;
}) {
  const ordered = [...questions].sort((a, b) => a.position - b.position);

  return (
    <ol data-testid="questions-list" className="flex flex-col gap-3">
      {ordered.map((question, index) => (
        <li key={question.id} data-testid="question-item" data-question-key={question.key}>
          <Card>
            <CardContent className="flex flex-wrap items-start justify-between gap-4">
              <div className="flex min-w-0 flex-col gap-2">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="text-xs text-muted-foreground">{index + 1}.</span>
                  <span className="font-medium">{question.statement}</span>
                  {question.required ? (
                    <Badge variant="outline">Obrigatória</Badge>
                  ) : null}
                </div>

                <div className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
                  <Badge variant="secondary">{QUESTION_TYPE_LABELS[question.type]}</Badge>
                  {question.range !== undefined ? (
                    <span>
                      faixa de {question.range.min} a {question.range.max}
                    </span>
                  ) : null}
                </div>

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

              {actionsFor !== undefined ? (
                <div className="flex shrink-0 items-center gap-1">{actionsFor(question)}</div>
              ) : null}
            </CardContent>
          </Card>
        </li>
      ))}
    </ol>
  );
}
