"use client";

import { PauseCircleIcon, PlayCircleIcon } from "lucide-react";
import { useState, useTransition } from "react";

import { Button } from "@/components/ui/button";
import { ConfirmDialog, FormError } from "@/shared/components";

import { setApplicationStatusAction } from "../actions";
import type { Application } from "../schemas/application";

/**
 * Desativar é reversível, mas muda o que o SDK recebe na hora — por isso pede confirmação e a
 * confirmação diz o que acontece com o histórico. Reativar não destrói nada e não pede.
 */
export function ApplicationStatusButton({ application }: { application: Application }) {
  const [pending, startTransition] = useTransition();
  const [error, setError] = useState<string>();

  const run = (action: "activate" | "deactivate") =>
    startTransition(async () => {
      const state = await setApplicationStatusAction(application.id, action);
      setError(state.status === "error" ? state.message : undefined);
    });

  if (application.status === "inactive") {
    return (
      <>
        <Button
          variant="outline"
          data-testid="activate-application-button"
          disabled={pending}
          onClick={() => run("activate")}
        >
          <PlayCircleIcon aria-hidden />
          Reativar
        </Button>
        <FormError message={error} />
      </>
    );
  }

  return (
    <>
      <ConfirmDialog
        trigger={
          <Button variant="destructive" data-testid="deactivate-application-button">
            <PauseCircleIcon aria-hidden />
            Desativar
          </Button>
        }
        title="Desativar esta aplicação?"
        description={`O SDK de "${application.name}" deixa de receber pesquisas imediatamente, mesmo as que estão ativas. Pesquisas, respondentes e respostas continuam acessíveis aqui. Dá para reativar depois.`}
        confirmLabel="Desativar"
        pending={pending}
        onConfirm={() => run("deactivate")}
      />
      <FormError message={error} />
    </>
  );
}
