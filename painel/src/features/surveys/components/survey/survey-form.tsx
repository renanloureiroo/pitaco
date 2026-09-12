"use client";

import { useActionState, useState } from "react";

import { Field, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";
import { FieldMessage, FormError, SubmitButton } from "@/shared/components";
import { idleFormState, type FormState } from "@/shared/lib";

import { createSurveyAction } from "../../actions";
import { SURVEY_TEMPLATE_OPTIONS } from "../../lib/survey-templates";

/**
 * Criar pede o nome e, se o autor quiser, um modelo pronto de onde partir. O modelo põe no
 * rascunho a pergunta do formato, editável como qualquer outra; em branco, nasce vazio.
 */
export function SurveyForm({ applicationId }: { applicationId: string }) {
  const [state, formAction, pending] = useActionState<FormState, FormData>(
    createSurveyAction.bind(null, applicationId),
    idleFormState,
  );

  const values = state.status === "error" ? state.values : {};
  const errors = state.status === "error" ? state.fieldErrors : {};
  const [template, setTemplate] = useState<string>(values.template ?? "blank");

  return (
    <form action={formAction} data-testid="survey-form" className="flex max-w-2xl flex-col gap-6">
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

      <fieldset className="flex flex-col gap-3">
        <legend className="mb-2 text-sm font-medium">Começar de</legend>
        <input type="hidden" name="template" value={template} />
        <div role="radiogroup" aria-label="Começar de" className="grid gap-3 sm:grid-cols-2">
          {SURVEY_TEMPLATE_OPTIONS.map((option) => {
            const selected = template === option.value;
            return (
              <button
                key={option.value}
                type="button"
                role="radio"
                aria-checked={selected}
                data-testid={`template-option-${option.value}`}
                onClick={() => setTemplate(option.value)}
                className={cn(
                  "flex flex-col gap-1 rounded-lg border p-4 text-left transition-colors",
                  "focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none",
                  selected ? "border-primary bg-primary/5" : "hover:bg-muted",
                )}
              >
                <span className="font-medium">{option.label}</span>
                <span className="text-sm text-muted-foreground">{option.description}</span>
              </button>
            );
          })}
        </div>
        <FieldMessage name="template" errors={errors} />
      </fieldset>

      <div>
        <SubmitButton pending={pending}>Criar pesquisa</SubmitButton>
      </div>
    </form>
  );
}
