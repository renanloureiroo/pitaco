"use client";

import { PlusIcon } from "lucide-react";
import { useActionState, useState } from "react";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Field, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { FieldMessage, FormError, SubmitButton } from "@/shared/components";
import { useFormStateChange } from "@/shared/hooks";
import { idleFormState, type FormState } from "@/shared/lib";

import { issueApiKeyAction } from "../actions";
import type { IssuedApiKey } from "../schemas/api-key";
import { SecretDialog } from "./secret-dialog";

export function IssueKeyForm({ applicationId }: { applicationId: string }) {
  const [open, setOpen] = useState(false);
  const [issued, setIssued] = useState<IssuedApiKey>();

  const [state, formAction, pending] = useActionState<
    FormState<IssuedApiKey | undefined>,
    FormData
  >(issueApiKeyAction.bind(null, applicationId), idleFormState);

  // O segredo sai do estado da action e passa a viver só aqui, até que o diálogo seja fechado.
  useFormStateChange(state, (next) => {
    if (next.status === "success" && next.data !== undefined) {
      setIssued(next.data);
      setOpen(false);
    }
  });

  const errors = state.status === "error" ? state.fieldErrors : {};

  return (
    <>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogTrigger asChild>
          <Button data-testid="issue-key-button">
            <PlusIcon aria-hidden />
            Emitir chave
          </Button>
        </DialogTrigger>
        <DialogContent>
          <form action={formAction} data-testid="issue-key-form" className="flex flex-col gap-6">
            <DialogHeader>
              <DialogTitle>Emitir chave de acesso</DialogTitle>
              <DialogDescription>
                O segredo aparece uma única vez, logo após a emissão.
              </DialogDescription>
            </DialogHeader>

            {state.status === "error" ? <FormError message={state.message} /> : null}

            <Field>
              <FieldLabel htmlFor="api-key-label">Rótulo</FieldLabel>
              <Input
                id="api-key-label"
                name="label"
                required
                maxLength={80}
                placeholder="Produção"
                defaultValue={state.status === "error" ? (state.values.label ?? "") : ""}
                aria-invalid={errors.label !== undefined}
              />
              <FieldMessage name="label" errors={errors} />
            </Field>

            <DialogFooter>
              <SubmitButton pending={pending}>Emitir</SubmitButton>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {issued !== undefined ? (
        <SecretDialog
          secret={issued.secret}
          label={issued.label}
          onClose={() => setIssued(undefined)}
        />
      ) : null}
    </>
  );
}
