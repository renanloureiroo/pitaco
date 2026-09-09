"use client";

import { Trash2Icon } from "lucide-react";
import { useState, useTransition } from "react";

import { Button } from "@/components/ui/button";
import { ConfirmDialog, FormError } from "@/shared/components";

import { discardSurveyAction } from "../../actions";

/**
 * Só é renderizado quando a pesquisa nunca foi publicada (FR-020) — a decisão fica em quem
 * monta a tela, com `publishedVersionNumber` em mãos. O `409` do backend continua tratado
 * porque o estado pode mudar entre a leitura e o clique.
 */
export function DiscardSurveyButton({
  applicationId,
  surveyId,
}: {
  applicationId: string;
  surveyId: string;
}) {
  const [pending, startTransition] = useTransition();
  const [error, setError] = useState<string>();

  function discard() {
    startTransition(async () => {
      const state = await discardSurveyAction(applicationId, surveyId);
      if (state.status === "error") {
        setError(state.message);
      }
    });
  }

  return (
    <div className="flex flex-col gap-2">
      <ConfirmDialog
        trigger={
          <Button variant="destructive" size="sm" data-testid="discard-survey-button">
            <Trash2Icon aria-hidden />
            Descartar pesquisa
          </Button>
        }
        title="Descartar esta pesquisa?"
        description="A pesquisa e tudo que foi montado nela desaparecem. Não há como desfazer."
        confirmLabel="Descartar"
        onConfirm={discard}
        pending={pending}
      />
      <FormError message={error} />
    </div>
  );
}
