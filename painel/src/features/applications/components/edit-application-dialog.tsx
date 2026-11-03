"use client";

import { PencilIcon } from "lucide-react";
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
import { Field, FieldDescription, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { FieldMessage, FormError, SubmitButton } from "@/shared/components";
import { useFormStateChange } from "@/shared/hooks";
import { idleFormState, type FormState } from "@/shared/lib";

import { updateApplicationAction } from "../actions";
import type { Application } from "../schemas/application";

/**
 * Edita nome e prazos. O slug não aparece porque é imutável. Cada prazo em branco é enviado
 * como remoção — a descrição do campo deixa isso dito, porque é o único lugar em que "vazio"
 * altera algo.
 */
export function EditApplicationDialog({ application }: { application: Application }) {
  const [open, setOpen] = useState(false);
  const [state, formAction, pending] = useActionState<FormState, FormData>(
    updateApplicationAction.bind(null, application.id),
    idleFormState,
  );

  useFormStateChange(state, (next) => {
    if (next.status === "success") {
      setOpen(false);
    }
  });

  const values = state.status === "error" ? state.values : undefined;
  const errors = state.status === "error" ? state.fieldErrors : {};

  const initial = (field: keyof Application): string => {
    if (values !== undefined) {
      return values[field] ?? "";
    }
    const value = application[field];
    return value === undefined ? "" : String(value);
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="outline" data-testid="edit-application-button">
          <PencilIcon aria-hidden />
          Editar
        </Button>
      </DialogTrigger>
      <DialogContent>
        <form action={formAction} data-testid="edit-application-form" className="flex flex-col gap-6">
          <DialogHeader>
            <DialogTitle>Editar aplicação</DialogTitle>
            <DialogDescription>
              O slug <span className="font-mono">{application.slug}</span> é imutável. Prazo em
              branco é removido.
            </DialogDescription>
          </DialogHeader>

          {state.status === "error" ? <FormError message={state.message} /> : null}

          <Field>
            <FieldLabel htmlFor="edit-name">Nome</FieldLabel>
            <Input
              id="edit-name"
              name="name"
              required
              maxLength={120}
              defaultValue={initial("name")}
              aria-invalid={errors.name !== undefined}
            />
            <FieldMessage name="name" errors={errors} />
          </Field>

          <Field>
            <FieldLabel htmlFor="edit-quietPeriodDays">Período de descanso (dias)</FieldLabel>
            <Input
              id="edit-quietPeriodDays"
              name="quietPeriodDays"
              inputMode="numeric"
              min={1}
              defaultValue={initial("quietPeriodDays")}
              aria-invalid={errors.quietPeriodDays !== undefined}
            />
            <FieldDescription>
              Dias sem nenhuma pesquisa para quem acabou de ver uma — vale entre pesquisas
              diferentes. Em branco remove o intervalo de descanso.
            </FieldDescription>
            <FieldMessage name="quietPeriodDays" errors={errors} />
          </Field>

          <Field>
            <FieldLabel htmlFor="edit-retentionDays">Retenção (dias)</FieldLabel>
            <Input
              id="edit-retentionDays"
              name="retentionDays"
              inputMode="numeric"
              min={1}
              defaultValue={initial("retentionDays")}
              aria-invalid={errors.retentionDays !== undefined}
            />
            <FieldDescription>Em branco, nada é descartado por tempo.</FieldDescription>
            <FieldMessage name="retentionDays" errors={errors} />
          </Field>

          <Field>
            <FieldLabel htmlFor="edit-openTextRetentionDays">
              Retenção de texto livre (dias)
            </FieldLabel>
            <Input
              id="edit-openTextRetentionDays"
              name="openTextRetentionDays"
              inputMode="numeric"
              min={1}
              defaultValue={initial("openTextRetentionDays")}
              aria-invalid={errors.openTextRetentionDays !== undefined}
            />
            <FieldDescription>Em branco, o texto livre segue o prazo geral.</FieldDescription>
            <FieldMessage name="openTextRetentionDays" errors={errors} />
          </Field>

          <DialogFooter>
            <SubmitButton pending={pending} testId="edit-application-submit">
              Salvar
            </SubmitButton>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
