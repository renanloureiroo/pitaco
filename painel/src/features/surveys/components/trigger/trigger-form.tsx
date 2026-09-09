"use client";

import { useActionState } from "react";

import { Field, FieldDescription, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { FieldMessage, FormError, SubmitButton } from "@/shared/components";
import { idleFormState, type FormState } from "@/shared/lib";

import { defineTriggerAction } from "../../actions";
import type { Trigger } from "../../schemas/trigger";

/** Data ISO UTC no formato que o `datetime-local` aceita. */
function toLocalInput(iso: string | undefined): string {
  if (iso === undefined) {
    return "";
  }

  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return "";
  }

  const pad = (value: number) => String(value).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(
    date.getHours(),
  )}:${pad(date.getMinutes())}`;
}

export function TriggerForm({
  applicationId,
  surveyId,
  trigger,
}: {
  applicationId: string;
  surveyId: string;
  trigger?: Trigger;
}) {
  const [state, formAction, pending] = useActionState<FormState, FormData>(
    defineTriggerAction.bind(null, applicationId, surveyId),
    idleFormState,
  );

  const values = state.status === "error" ? state.values : {};
  const errors = state.status === "error" ? state.fieldErrors : {};

  return (
    <form action={formAction} data-testid="trigger-form" className="flex max-w-xl flex-col gap-6">
      {state.status === "error" ? <FormError message={state.message} /> : null}

      <Field>
        <FieldLabel htmlFor="eventName">Evento</FieldLabel>
        <Input
          id="eventName"
          name="eventName"
          data-testid="event-name-input"
          required
          placeholder="checkout.completed"
          defaultValue={values.eventName ?? trigger?.eventName ?? ""}
          aria-invalid={errors.eventName !== undefined}
        />
        <FieldDescription>
          Letras minúsculas, números, ponto e sublinhado, começando por letra.
        </FieldDescription>
        <FieldMessage name="eventName" errors={errors} />
      </Field>

      <div className="grid gap-4 sm:grid-cols-2">
        <Field>
          <FieldLabel htmlFor="windowStart">Início da janela</FieldLabel>
          <Input
            id="windowStart"
            name="windowStart"
            data-testid="window-start-input"
            type="datetime-local"
            required
            defaultValue={values.windowStart ?? toLocalInput(trigger?.windowStart)}
            aria-invalid={errors.windowStart !== undefined}
          />
          <FieldMessage name="windowStart" errors={errors} />
        </Field>

        <Field>
          <FieldLabel htmlFor="windowEnd">Fim da janela</FieldLabel>
          <Input
            id="windowEnd"
            name="windowEnd"
            data-testid="window-end-input"
            type="datetime-local"
            defaultValue={values.windowEnd ?? toLocalInput(trigger?.windowEnd)}
            aria-invalid={errors.windowEnd !== undefined}
          />
          <FieldDescription>Em branco deixa a janela aberta.</FieldDescription>
          <FieldMessage name="windowEnd" errors={errors} />
        </Field>
      </div>

      <Field>
        <FieldLabel htmlFor="samplingRate">Taxa de amostragem</FieldLabel>
        <Input
          id="samplingRate"
          name="samplingRate"
          data-testid="sampling-rate-input"
          inputMode="decimal"
          placeholder="0.0"
          defaultValue={values.samplingRate ?? trigger?.samplingRate ?? ""}
          aria-invalid={errors.samplingRate !== undefined}
        />
        <FieldDescription>De 0 a 1. Em branco assume 0.</FieldDescription>
        <FieldMessage name="samplingRate" errors={errors} />
      </Field>

      <div>
        <SubmitButton pending={pending}>
          {trigger === undefined ? "Definir disparo" : "Redefinir disparo"}
        </SubmitButton>
      </div>
    </form>
  );
}
