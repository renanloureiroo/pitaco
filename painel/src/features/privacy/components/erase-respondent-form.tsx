"use client";

import { Trash2Icon } from "lucide-react";
import { useActionState, useRef, useState } from "react";

import { Button } from "@/components/ui/button";
import { Field, FieldDescription, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { ConfirmDialog, FieldMessage, FormError } from "@/shared/components";
import { idleFormState, type FormState } from "@/shared/lib";

import { eraseRespondentAction } from "../actions";
import { ERASURE_TITLE, erasureDescription, erasureResultMessage } from "../lib/privacy-labels";
import { IDENTITY_MAX } from "../schemas/forms";
import type { RespondentErasure } from "../schemas/privacy";

/**
 * O botão não envia: abre a confirmação, e só confirmar submete o formulário. Cancelar fecha sem
 * tocar em nada.
 */
export function EraseRespondentForm({ applicationId }: { applicationId: string }) {
  const [state, formAction, pending] = useActionState<FormState<RespondentErasure>, FormData>(
    eraseRespondentAction.bind(null, applicationId),
    idleFormState,
  );
  const formRef = useRef<HTMLFormElement>(null);
  const [identity, setIdentity] = useState("");

  const errors = state.status === "error" ? state.fieldErrors : {};

  return (
    <form
      ref={formRef}
      action={formAction}
      data-testid="erase-respondent-form"
      className="flex max-w-xl flex-col gap-4"
    >
      {state.status === "error" ? <FormError message={state.message} /> : null}

      <div className="grid gap-4 sm:grid-cols-[12rem_1fr]">
        <Field>
          <FieldLabel htmlFor="identityKind">Identificar por</FieldLabel>
          <Select name="identityKind" defaultValue="reference">
            <SelectTrigger id="identityKind" data-testid="erase-identity-kind">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="reference">Referência do app</SelectItem>
              <SelectItem value="device">Dispositivo</SelectItem>
            </SelectContent>
          </Select>
        </Field>

        <Field>
          <FieldLabel htmlFor="identity">Identificação</FieldLabel>
          <Input
            id="identity"
            name="identity"
            data-testid="erase-identity-input"
            maxLength={IDENTITY_MAX}
            value={identity}
            onChange={(event) => setIdentity(event.target.value)}
            aria-invalid={errors.identity !== undefined}
          />
          <FieldDescription>
            Como o app enviou ao SDK. A referência vale para quem o app identifica; o dispositivo,
            para quem nunca teve referência.
          </FieldDescription>
          <FieldMessage name="identity" errors={errors} />
        </Field>
      </div>

      <div>
        <ConfirmDialog
          trigger={
            <Button
              type="button"
              variant="destructive"
              data-testid="erase-respondent-button"
              disabled={pending || identity.trim() === ""}
            >
              <Trash2Icon aria-hidden />
              Excluir respondente
            </Button>
          }
          title={ERASURE_TITLE}
          description={erasureDescription(identity.trim())}
          confirmLabel="Excluir definitivamente"
          pending={pending}
          onConfirm={() => formRef.current?.requestSubmit()}
        />
      </div>

      {state.status === "success" ? (
        <p data-testid="erasure-result" role="status" className="text-sm">
          {erasureResultMessage(state.data)}
        </p>
      ) : null}
    </form>
  );
}
