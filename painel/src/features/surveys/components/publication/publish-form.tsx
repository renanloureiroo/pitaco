"use client";

import { useActionState, useState } from "react";

import { Field, FieldDescription, FieldLabel } from "@/components/ui/field";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { FieldMessage, FormError, SubmitButton } from "@/shared/components";
import { idleFormState, type FormState } from "@/shared/lib";

import { publishSurveyAction } from "../../actions";
import { CHANGE_KINDS, CHANGE_KIND_LABELS } from "../../schemas/publication";

/**
 * Duas garantias vivem aqui: o botão fica desabilitado enquanto houver **qualquer** impedimento
 * (FR-030), e fica desabilitado enquanto a action está pendente — clique duplo não cria duas
 * versões.
 *
 * Os campos de mudança só existem a partir da versão 2; na primeira, não há mudança a
 * classificar.
 */
export function PublishForm({
  applicationId,
  surveyId,
  hasImpediments,
  hasPublishedVersion,
}: {
  applicationId: string;
  surveyId: string;
  hasImpediments: boolean;
  hasPublishedVersion: boolean;
}) {
  const [state, formAction, pending] = useActionState<FormState, FormData>(
    publishSurveyAction.bind(null, applicationId, surveyId, hasPublishedVersion),
    idleFormState,
  );

  const values = state.status === "error" ? state.values : {};
  const errors = state.status === "error" ? state.fieldErrors : {};
  const [changeKind, setChangeKind] = useState(values.changeKind ?? "");

  return (
    <form action={formAction} data-testid="publish-form" className="flex max-w-xl flex-col gap-6">
      {state.status === "error" ? <FormError message={state.message} /> : null}

      {hasPublishedVersion ? (
        <>
          <Field>
            <FieldLabel htmlFor="change-kind">Natureza da mudança</FieldLabel>
            <Select name="changeKind" value={changeKind} onValueChange={setChangeKind}>
              <SelectTrigger id="change-kind" data-testid="change-kind-select">
                <SelectValue placeholder="Escolha a natureza da mudança" />
              </SelectTrigger>
              <SelectContent>
                {CHANGE_KINDS.map((kind) => (
                  <SelectItem key={kind} value={kind}>
                    {CHANGE_KIND_LABELS[kind]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <FieldMessage name="changeKind" errors={errors} />
          </Field>

          <Field>
            <FieldLabel htmlFor="change-summary">Resumo da mudança</FieldLabel>
            <Textarea
              id="change-summary"
              name="changeSummary"
              data-testid="change-summary-input"
              rows={2}
              maxLength={500}
              defaultValue={values.changeSummary ?? ""}
            />
            <FieldDescription>Opcional, para quem for ler o histórico depois.</FieldDescription>
            <FieldMessage name="changeSummary" errors={errors} />
          </Field>
        </>
      ) : null}

      <div>
        <SubmitButton pending={pending} disabled={hasImpediments} testId="publish-button">
          Publicar
        </SubmitButton>
        {hasImpediments ? (
          <p className="mt-2 text-sm text-muted-foreground">
            Resolva os impedimentos acima para liberar a publicação.
          </p>
        ) : null}
      </div>
    </form>
  );
}
