"use client";

import { ChevronDownIcon, ChevronUpIcon } from "lucide-react";
import { useState, useTransition } from "react";

import { Button } from "@/components/ui/button";
import { FormError } from "@/shared/components";

import { moveQuestionAction } from "../../actions";

/**
 * Mover envia a **permutação completa** da versão, montada a partir da ordem que a tela está
 * exibindo. Com uma única pergunta a ação não é oferecida: não há para onde mover.
 */
export function MoveQuestionButtons({
  applicationId,
  surveyId,
  questionIds,
  questionId,
}: {
  applicationId: string;
  surveyId: string;
  questionIds: string[];
  questionId: string;
}) {
  const [pending, startTransition] = useTransition();
  const [error, setError] = useState<string>();

  if (questionIds.length < 2) {
    return null;
  }

  const index = questionIds.indexOf(questionId);

  function move(delta: -1 | 1) {
    startTransition(async () => {
      const state = await moveQuestionAction(
        applicationId,
        surveyId,
        questionIds,
        questionId,
        delta,
      );
      if (state.status === "error") {
        setError(state.message);
      }
    });
  }

  return (
    <>
      <Button
        type="button"
        variant="ghost"
        size="icon"
        data-testid="move-question-up"
        aria-label="Mover para cima"
        disabled={pending || index <= 0}
        onClick={() => move(-1)}
      >
        <ChevronUpIcon aria-hidden />
      </Button>
      <Button
        type="button"
        variant="ghost"
        size="icon"
        data-testid="move-question-down"
        aria-label="Mover para baixo"
        disabled={pending || index === -1 || index >= questionIds.length - 1}
        onClick={() => move(1)}
      >
        <ChevronDownIcon aria-hidden />
      </Button>
      <FormError message={error} />
    </>
  );
}
