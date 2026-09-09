"use client";

import { FilePlus2Icon, Trash2Icon } from "lucide-react";
import { useState, useTransition } from "react";

import { Button } from "@/components/ui/button";
import { ConfirmDialog, FormError } from "@/shared/components";

import { discardDraftVersionAction, openDraftVersionAction } from "../../actions";

/**
 * Abrir rascunho permite editar sem alterar o que está no ar; descartá-lo devolve a pesquisa ao
 * conteúdo publicado — e por isso pede confirmação.
 */
export function DraftVersionActions({
  applicationId,
  surveyId,
  hasDraft,
  canOpenDraft,
}: {
  applicationId: string;
  surveyId: string;
  hasDraft: boolean;
  canOpenDraft: boolean;
}) {
  const [pending, startTransition] = useTransition();
  const [error, setError] = useState<string>();

  function run(action: () => Promise<{ status: string; message?: string }>) {
    startTransition(async () => {
      const state = await action();
      setError(state.status === "error" ? state.message : undefined);
    });
  }

  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap items-center gap-2">
        {canOpenDraft && !hasDraft ? (
          <Button
            variant="outline"
            size="sm"
            data-testid="open-draft-version-button"
            disabled={pending}
            onClick={() => run(() => openDraftVersionAction(applicationId, surveyId))}
          >
            <FilePlus2Icon aria-hidden />
            Abrir versão de rascunho
          </Button>
        ) : null}

        {hasDraft ? (
          <ConfirmDialog
            trigger={
              <Button variant="destructive" size="sm" data-testid="discard-draft-version-button">
                <Trash2Icon aria-hidden />
                Descartar rascunho de versão
              </Button>
            }
            title="Descartar o rascunho de versão?"
            description="Tudo que foi editado neste rascunho é perdido. A versão publicada continua no ar."
            confirmLabel="Descartar"
            pending={pending}
            onConfirm={() => run(() => discardDraftVersionAction(applicationId, surveyId))}
          />
        ) : null}
      </div>

      <FormError message={error} />
    </div>
  );
}
