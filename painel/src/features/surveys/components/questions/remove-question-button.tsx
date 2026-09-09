"use client";

import { Trash2Icon } from "lucide-react";
import { useState, useTransition } from "react";

import { Button } from "@/components/ui/button";
import { ConfirmDialog, FormError } from "@/shared/components";

import { removeQuestionAction } from "../../actions";

export function RemoveQuestionButton({
  applicationId,
  surveyId,
  questionId,
  statement,
}: {
  applicationId: string;
  surveyId: string;
  questionId: string;
  statement: string;
}) {
  const [pending, startTransition] = useTransition();
  const [error, setError] = useState<string>();

  return (
    <>
      <ConfirmDialog
        trigger={
          <Button
            variant="ghost"
            size="icon"
            data-testid="remove-question-button"
            aria-label={`Remover pergunta "${statement}"`}
          >
            <Trash2Icon aria-hidden />
          </Button>
        }
        title="Remover esta pergunta?"
        description={`"${statement}" sai da montagem desta versão. Não há como desfazer.`}
        confirmLabel="Remover"
        pending={pending}
        onConfirm={() =>
          startTransition(async () => {
            const state = await removeQuestionAction(applicationId, surveyId, questionId);
            if (state.status === "error") {
              setError(state.message);
            }
          })
        }
      />
      <FormError message={error} />
    </>
  );
}
