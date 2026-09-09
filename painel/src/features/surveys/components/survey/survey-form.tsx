"use client";

import { useActionState } from "react";

import { Field, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { FieldMessage, FormError, SubmitButton } from "@/shared/components";
import { idleFormState, type FormState } from "@/shared/lib";

import { createSurveyAction } from "../../actions";

/** Criar exige apenas o nome: a pesquisa nasce em rascunho, sem perguntas e sem disparo. */
export function SurveyForm({ applicationId }: { applicationId: string }) {
  const [state, formAction, pending] = useActionState<FormState, FormData>(
    createSurveyAction.bind(null, applicationId),
    idleFormState,
  );

  const values = state.status === "error" ? state.values : {};
  const errors = state.status === "error" ? state.fieldErrors : {};

  return (
    <form action={formAction} data-testid="survey-form" className="flex max-w-xl flex-col gap-6">
      {state.status === "error" ? <FormError message={state.message} /> : null}

      <Field>
        <FieldLabel htmlFor="name">Nome</FieldLabel>
        <Input
          id="name"
          name="name"
          required
          maxLength={160}
          defaultValue={values.name ?? ""}
          aria-invalid={errors.name !== undefined}
        />
        <FieldMessage name="name" errors={errors} />
      </Field>

      <div>
        <SubmitButton pending={pending}>Criar pesquisa</SubmitButton>
      </div>
    </form>
  );
}
