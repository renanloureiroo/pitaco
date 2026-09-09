"use client";

import { useActionState } from "react";

import { Field, FieldDescription, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { FieldMessage, FormError, SubmitButton } from "@/shared/components";
import { idleFormState, type FormState } from "@/shared/lib";

import { createApplicationAction } from "../actions";

/**
 * `"use client"` porque `useActionState` precisa de estado de cliente — é o menor componente
 * que pode carregar a interatividade desta tela.
 *
 * O que foi digitado é reposto por `defaultValue` a partir do estado devolvido pela action
 * (FR-004): uma recusa do backend não pode esvaziar o formulário.
 */
export function ApplicationForm() {
  const [state, formAction, pending] = useActionState<FormState, FormData>(
    createApplicationAction,
    idleFormState,
  );

  const values = state.status === "error" ? state.values : {};
  const errors = state.status === "error" ? state.fieldErrors : {};

  return (
    <form action={formAction} data-testid="application-form" className="flex max-w-xl flex-col gap-6">
      {state.status === "error" ? <FormError message={state.message} /> : null}

      <Field>
        <FieldLabel htmlFor="name">Nome</FieldLabel>
        <Input
          id="name"
          name="name"
          required
          maxLength={120}
          defaultValue={values.name ?? ""}
          aria-invalid={errors.name !== undefined}
        />
        <FieldMessage name="name" errors={errors} />
      </Field>

      <Field>
        <FieldLabel htmlFor="slug">Slug</FieldLabel>
        <Input
          id="slug"
          name="slug"
          maxLength={50}
          placeholder="minha-app"
          defaultValue={values.slug ?? ""}
          aria-invalid={errors.slug !== undefined}
        />
        <FieldDescription>
          Opcional. Deixando em branco, o backend deriva o slug a partir do nome.
        </FieldDescription>
        <FieldMessage name="slug" errors={errors} />
      </Field>

      <Field>
        <FieldLabel htmlFor="quietPeriodDays">Período de descanso (dias)</FieldLabel>
        <Input
          id="quietPeriodDays"
          name="quietPeriodDays"
          inputMode="numeric"
          min={1}
          defaultValue={values.quietPeriodDays ?? ""}
          aria-invalid={errors.quietPeriodDays !== undefined}
        />
        <FieldDescription>
          Opcional. Em branco significa não configurado — não zero.
        </FieldDescription>
        <FieldMessage name="quietPeriodDays" errors={errors} />
      </Field>

      <Field>
        <FieldLabel htmlFor="retentionDays">Retenção (dias)</FieldLabel>
        <Input
          id="retentionDays"
          name="retentionDays"
          inputMode="numeric"
          min={1}
          defaultValue={values.retentionDays ?? ""}
          aria-invalid={errors.retentionDays !== undefined}
        />
        <FieldMessage name="retentionDays" errors={errors} />
      </Field>

      <Field>
        <FieldLabel htmlFor="openTextRetentionDays">Retenção de texto livre (dias)</FieldLabel>
        <Input
          id="openTextRetentionDays"
          name="openTextRetentionDays"
          inputMode="numeric"
          min={1}
          defaultValue={values.openTextRetentionDays ?? ""}
          aria-invalid={errors.openTextRetentionDays !== undefined}
        />
        <FieldMessage name="openTextRetentionDays" errors={errors} />
      </Field>

      <div>
        <SubmitButton pending={pending}>Cadastrar aplicação</SubmitButton>
      </div>
    </form>
  );
}
