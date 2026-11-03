import type { ReactNode } from "react";

import { describeCondition } from "../../lib/condition";
import type { Question } from "../../schemas/question";
import { QuestionCard } from "./question-card";

/**
 * As perguntas são exibidas na ordem de `position`, que é a verdade do backend — a ordem em
 * que o array chegou não é garantia. Lista fixa: a reordenável é `SortableQuestionsList`.
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
          <QuestionCard
            question={question}
            index={index}
            actions={actionsFor?.(question)}
            conditionSummary={
              question.condition === undefined
                ? undefined
                : describeCondition(question.condition, ordered)
            }
          />
        </li>
      ))}
    </ol>
  );
}
