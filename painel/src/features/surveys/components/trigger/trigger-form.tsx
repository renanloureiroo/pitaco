"use client";

import { useActionState, useState } from "react";

import { Button } from "@/components/ui/button";
import { Field, FieldDescription, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { FieldMessage, FormError, SubmitButton } from "@/shared/components";
import { idleFormState, toLocalInput, type FormState } from "@/shared/lib";

import { defineTriggerAction } from "../../actions";
import type { Trigger } from "../../schemas/trigger";

function windowValue(iso: string | undefined): string {
  return toLocalInput(iso) ?? "";
}

/**
 * O evento é digitação livre com sugestão: os já vistos na aplicação viram atalhos que
 * preenchem o campo, mas o evento pode ainda não ter sido disparado por ninguém.
 */
export function TriggerForm({
  applicationId,
  surveyId,
  trigger,
  observedEvents = [],
}: {
  applicationId: string;
  surveyId: string;
  trigger?: Trigger;
  observedEvents?: string[];
}) {
  const [state, formAction, pending] = useActionState<FormState, FormData>(
    defineTriggerAction.bind(null, applicationId, surveyId),
    idleFormState,
  );

  const values = state.status === "error" ? state.values : {};
  const errors = state.status === "error" ? state.fieldErrors : {};

  // Controlado só para que a sugestão consiga preencher o campo; o valor enviado continua
  // sendo o do `FormData`, como nos demais.
  const [eventName, setEventName] = useState(values.eventName ?? trigger?.eventName ?? "");

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
          value={eventName}
          onChange={(event) => setEventName(event.target.value)}
          aria-invalid={errors.eventName !== undefined}
        />
        <FieldDescription>
          Letras minúsculas, números, ponto e sublinhado, começando por letra.
        </FieldDescription>
        <FieldMessage name="eventName" errors={errors} />
        {observedEvents.length === 0 ? (
          <p data-testid="observed-events-empty" className="text-sm text-muted-foreground">
            Esta aplicação ainda não disparou nenhum evento; digite o nome que o app vai usar.
          </p>
        ) : (
          <div data-testid="observed-events" className="flex flex-col gap-2">
            <p className="text-sm text-muted-foreground">Já vistos nesta aplicação:</p>
            <ul className="flex flex-wrap gap-2" aria-label="Eventos já observados">
              {observedEvents.map((name) => (
                <li key={name}>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    data-testid="observed-event-suggestion"
                    aria-pressed={eventName === name}
                    onClick={() => setEventName(name)}
                  >
                    <span className="font-mono">{name}</span>
                  </Button>
                </li>
              ))}
            </ul>
          </div>
        )}
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
            defaultValue={values.windowStart ?? windowValue(trigger?.windowStart)}
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
            defaultValue={values.windowEnd ?? windowValue(trigger?.windowEnd)}
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
