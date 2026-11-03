"use client";

import { BanIcon } from "lucide-react";
import { useState, useTransition } from "react";

import { Button } from "@/components/ui/button";
import { ConfirmDialog, FormError } from "@/shared/components";

import { revokeApiKeyAction } from "../actions";

/** Renderizado apenas para chaves ativas (FR-015): revogar o já revogado não é uma ação. */
export function RevokeKeyButton({
  applicationId,
  apiKeyId,
  label,
}: {
  applicationId: string;
  apiKeyId: string;
  label: string;
}) {
  const [pending, startTransition] = useTransition();
  const [error, setError] = useState<string>();

  return (
    <>
      <ConfirmDialog
        trigger={
          <Button
            variant="destructive"
            size="sm"
            data-testid="revoke-key-button"
            aria-label={`Revogar chave "${label}"`}
          >
            <BanIcon aria-hidden />
            Revogar
          </Button>
        }
        title="Revogar esta chave?"
        description={`Tudo que usa a chave "${label}" deixa de ser aceito imediatamente. Não há como desfazer.`}
        confirmLabel="Revogar"
        pending={pending}
        onConfirm={() =>
          startTransition(async () => {
            const state = await revokeApiKeyAction(applicationId, apiKeyId);
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
