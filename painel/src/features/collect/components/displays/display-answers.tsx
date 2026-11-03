import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { QUESTION_TYPE_LABELS, type Question } from "@/features/surveys";

import { answerValueText, type ResolvedAnswer } from "../../lib/answers";
import {
  ANSWER_BLANK,
  ANSWER_EXPIRED_EXPLANATION,
  ANSWER_NOT_APPLICABLE_EXPLANATION,
  ANSWER_SKIPPED_EXPLANATION,
  ANSWER_STATUS_LABELS,
  QUESTION_NOT_IN_VERSION,
} from "../../lib/collect-labels";
import type { DisplayOutcome } from "../../schemas/display";

export function DisplayAnswers({
  answers,
  outcome,
  versionAvailable,
}: {
  answers: ResolvedAnswer[];
  outcome: DisplayOutcome;
  versionAvailable: boolean;
}) {
  return (
    <Card data-testid="display-answers">
      <CardHeader>
        <CardTitle>Respostas</CardTitle>
        {versionAvailable ? null : (
          <p data-testid="version-unavailable-note" className="text-sm text-muted-foreground">
            Não foi possível ler a versão exibida, então os enunciados não puderam ser
            carregados. As respostas aparecem identificadas pela chave da pergunta.
          </p>
        )}
      </CardHeader>
      <CardContent>
        {answers.length === 0 ? (
          <p data-testid="answers-empty" className="text-sm text-muted-foreground">
            {emptyReason(outcome)}
          </p>
        ) : (
          <ol className="flex flex-col gap-4">
            {answers.map(({ answer, question }, index) => (
              <li
                key={`${answer.questionKey}-${index}`}
                data-testid="answer-item"
                className="flex flex-col gap-1 border-b pb-4 last:border-b-0 last:pb-0"
              >
                <p data-testid="answer-question" className="text-sm font-medium">
                  {question === undefined ? (
                    <span className="font-mono">{answer.questionKey}</span>
                  ) : (
                    question.statement
                  )}
                </p>
                <p className="text-xs text-muted-foreground">{typeLine(question)}</p>
                <AnswerValue answer={answer} />
              </li>
            ))}
          </ol>
        )}
      </CardContent>
    </Card>
  );
}

function typeLine(question: Question | undefined): string {
  return question === undefined
    ? QUESTION_NOT_IN_VERSION
    : QUESTION_TYPE_LABELS[question.type];
}

function emptyReason(outcome: DisplayOutcome): string {
  switch (outcome) {
    case "STARTED":
      return "Sem respostas: esta exibição ainda está aberta e não foi concluída.";
    case "DISMISSED":
      return "Sem respostas: quem viu esta exibição a dispensou sem responder.";
    case "COMPLETED":
      return "Sem respostas registradas nesta exibição concluída.";
  }
}

function AnswerValue({ answer }: { answer: ResolvedAnswer["answer"] }) {
  if (answer.status === "SKIPPED") {
    return (
      <p data-testid="answer-skipped" className="text-sm">
        <span className="font-medium">{ANSWER_STATUS_LABELS.SKIPPED}</span>{" "}
        <span className="text-muted-foreground">{ANSWER_SKIPPED_EXPLANATION}</span>
      </p>
    );
  }

  if (answer.status === "NOT_APPLICABLE") {
    return (
      <p data-testid="answer-not-applicable" className="text-sm">
        <span className="font-medium">{ANSWER_STATUS_LABELS.NOT_APPLICABLE}</span>{" "}
        <span className="text-muted-foreground">{ANSWER_NOT_APPLICABLE_EXPLANATION}</span>
      </p>
    );
  }

  if (answer.status === "EXPIRED") {
    return (
      <p data-testid="answer-expired" className="text-sm">
        <span className="font-medium">{ANSWER_STATUS_LABELS.EXPIRED}</span>{" "}
        <span className="text-muted-foreground">{ANSWER_EXPIRED_EXPLANATION}</span>
      </p>
    );
  }

  const value = answerValueText(answer);

  return (
    <p data-testid="answer-value" className="text-sm">
      {value ?? <span className="text-muted-foreground">{ANSWER_BLANK}</span>}
    </p>
  );
}
