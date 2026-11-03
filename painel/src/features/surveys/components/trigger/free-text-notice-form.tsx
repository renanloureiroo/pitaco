"use client";

import { useActionState, useState } from "react";

import { Checkbox } from "@/components/ui/checkbox";
import { Field, FieldDescription, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { FieldMessage, FormError, SubmitButton } from "@/shared/components";
import { idleFormState, type FormState } from "@/shared/lib";

import { updateFreeTextNoticeAction } from "../../actions";
import { FREE_TEXT_NOTICE_MAX } from "../../schemas/notice";
import type { FreeTextNotice } from "../../schemas/survey";

/**
 * Mesmo arranjo do formulário de exposição: o estado ligado vai num campo escondido com
 * `true`/`false` explícitos, porque desligar precisa chegar ao backend como `false`.
 */
export function FreeTextNoticeForm({
  applicationId,
  surveyId,
  notice,
}: {
  applicationId: string;
  surveyId: string;
  notice: FreeTextNotice;
}) {
  const [state, formAction, pending] = useActionState<FormState, FormData>(
    updateFreeTextNoticeAction.bind(null, applicationId, surveyId),
    idleFormState,
  );

  const values = state.status === "error" ? state.values : {};
  const errors = state.status === "error" ? state.fieldErrors : {};
  const [enabled, setEnabled] = useState(notice.enabled);

  return (
    <form
      action={formAction}
      data-testid="free-text-notice-form"
      className="flex max-w-xl flex-col gap-6"
    >
      {state.status === "error" ? <FormError message={state.message} /> : null}

      <Field orientation="horizontal">
        <input type="hidden" name="enabled" value={enabled ? "true" : "false"} />
        <Checkbox
          id="freeTextNoticeEnabled"
          data-testid="free-text-notice-checkbox"
          checked={enabled}
          onCheckedChange={(checked) => setEnabled(checked === true)}
        />
        <div className="flex flex-col gap-1">
          <FieldLabel htmlFor="freeTextNoticeEnabled">
            Mostrar o aviso junto dos campos de texto livre
          </FieldLabel>
          <FieldDescription>
            Pede ao respondente que não escreva dado pessoal. Não impede ninguém de escrever:
            reduz a chance na origem.
          </FieldDescription>
        </div>
      </Field>

      <Field>
        <FieldLabel htmlFor="customText">Texto do aviso</FieldLabel>
        <Input
          id="customText"
          name="customText"
          data-testid="free-text-notice-input"
          maxLength={FREE_TEXT_NOTICE_MAX}
          placeholder={notice.defaultText}
          defaultValue={values.customText ?? notice.customText ?? ""}
          aria-invalid={errors.customText !== undefined}
        />
        <FieldDescription>
          Em branco, vale o texto padrão. Curto e humano: aviso longo não é lido, e aviso jurídico
          assusta quem ia responder.
        </FieldDescription>
        <FieldMessage name="customText" errors={errors} />
        <FieldMessage name="freeTextNoticeText" errors={errors} />
      </Field>

      <div>
        <SubmitButton pending={pending} testId="save-free-text-notice-button">
          Salvar aviso
        </SubmitButton>
      </div>
    </form>
  );
}
