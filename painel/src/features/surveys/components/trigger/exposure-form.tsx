"use client";

import { useActionState, useState } from "react";

import { Checkbox } from "@/components/ui/checkbox";
import { Field, FieldDescription, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { FieldMessage, FormError, SubmitButton } from "@/shared/components";
import { idleFormState, type FormState } from "@/shared/lib";

import { updateExposureAction } from "../../actions";
import { PRIORITY_MAX, PRIORITY_MIN } from "../../schemas/exposure";

/**
 * A isenção vai num campo escondido com `true`/`false` explícitos: o checkbox do Radix só
 * envia algo quando marcado, e desmarcar precisa chegar ao backend como `false`.
 */
export function ExposureForm({
  applicationId,
  surveyId,
  priority,
  responseQuota,
  ignoresQuietPeriod,
}: {
  applicationId: string;
  surveyId: string;
  priority: number;
  responseQuota?: number;
  ignoresQuietPeriod: boolean;
}) {
  const [state, formAction, pending] = useActionState<FormState, FormData>(
    updateExposureAction.bind(null, applicationId, surveyId),
    idleFormState,
  );

  const values = state.status === "error" ? state.values : {};
  const errors = state.status === "error" ? state.fieldErrors : {};
  const [ignores, setIgnores] = useState(ignoresQuietPeriod);

  return (
    <form action={formAction} data-testid="exposure-form" className="flex max-w-xl flex-col gap-6">
      {state.status === "error" ? <FormError message={state.message} /> : null}

      <div className="grid gap-4 sm:grid-cols-2">
        <Field>
          <FieldLabel htmlFor="priority">Prioridade</FieldLabel>
          <Input
            id="priority"
            name="priority"
            data-testid="priority-input"
            inputMode="numeric"
            min={PRIORITY_MIN}
            max={PRIORITY_MAX}
            defaultValue={values.priority ?? String(priority)}
            aria-invalid={errors.priority !== undefined}
          />
          <FieldDescription>
            De {PRIORITY_MIN} a {PRIORITY_MAX}. Maior vence o desempate.
          </FieldDescription>
          <FieldMessage name="priority" errors={errors} />
        </Field>

        <Field>
          <FieldLabel htmlFor="responseQuota">Cota de respostas</FieldLabel>
          <Input
            id="responseQuota"
            name="responseQuota"
            data-testid="quota-input"
            inputMode="numeric"
            min={1}
            defaultValue={values.responseQuota ?? (responseQuota === undefined ? "" : String(responseQuota))}
            aria-invalid={errors.responseQuota !== undefined}
          />
          <FieldDescription>
            Atingida, a pesquisa encerra sozinha. Em branco, não há cota.
          </FieldDescription>
          <FieldMessage name="responseQuota" errors={errors} />
        </Field>
      </div>

      <Field orientation="horizontal">
        <input type="hidden" name="ignoresQuietPeriod" value={ignores ? "true" : "false"} />
        <Checkbox
          id="ignoresQuietPeriod"
          data-testid="ignore-quiet-period-checkbox"
          checked={ignores}
          onCheckedChange={(checked) => setIgnores(checked === true)}
        />
        <div className="flex flex-col gap-1">
          <FieldLabel htmlFor="ignoresQuietPeriod">Ignorar o intervalo de descanso</FieldLabel>
          <FieldDescription data-testid="quiet-period-exemption-notice">
            {ignores
              ? "Ligada, esta pesquisa aparece mesmo para quem acabou de ver outra — ela gasta a paciência reservada às demais pesquisas da aplicação."
              : "Desligada, quem viu qualquer pesquisa da aplicação há pouco não recebe esta até o intervalo passar."}
          </FieldDescription>
        </div>
      </Field>

      <div>
        <SubmitButton pending={pending} testId="save-exposure-button">
          Salvar exposição
        </SubmitButton>
      </div>
    </form>
  );
}
