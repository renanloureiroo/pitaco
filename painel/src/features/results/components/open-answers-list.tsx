import { formatDateTime } from "@/shared/lib";

import type { OpenAnswer } from "../schemas/results";

/**
 * Texto com o contexto da mesma exibição: "achei confuso" vindo de quem deu 9 é outra coisa
 * vindo de quem deu 2. O contexto fica recolhido para a lista continuar varrível.
 */
export function OpenAnswersList({ answers }: { answers: OpenAnswer[] }) {
  return (
    <ol data-testid="open-answers-list" className="flex flex-col gap-3">
      {answers.map((answer) => (
        <li
          key={`${answer.displayId}-${answer.questionKey}`}
          data-testid="open-answer"
          className="flex flex-col gap-2 rounded-lg border p-4"
        >
          <p data-testid="open-answer-text" className="text-sm whitespace-pre-wrap">
            {answer.text}
          </p>
          <p className="text-xs text-muted-foreground">
            {answer.statement} · {formatDateTime(answer.answeredAt)}
          </p>
          {answer.context.length > 0 ? (
            <details data-testid="open-answer-context">
              <summary className="cursor-pointer text-xs text-muted-foreground">
                O que mais essa pessoa respondeu ({answer.context.length})
              </summary>
              <dl className="mt-2 grid gap-2 text-sm sm:grid-cols-2">
                {answer.context.map((entry) => (
                  <div key={entry.questionKey} className="flex flex-col">
                    <dt className="text-xs text-muted-foreground">{entry.statement}</dt>
                    <dd data-testid="context-value">{entry.value}</dd>
                  </div>
                ))}
              </dl>
            </details>
          ) : null}
        </li>
      ))}
    </ol>
  );
}
