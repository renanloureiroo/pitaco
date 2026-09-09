"use client";

import { PlusIcon } from "lucide-react";
import { useActionState, useEffect, useState } from "react";

import { Field, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { FieldMessage, FormError, SubmitButton } from "@/shared/components";
import { idleFormState, type FormState } from "@/shared/lib";

import { addSegmentationRuleAction } from "../../actions";
import { RULE_OPERATION_LABELS } from "../../lib/survey-labels";
import { RULE_OPERATIONS, requiresValue, type RuleOperation } from "../../schemas/trigger";

function isRuleOperation(value: unknown): value is RuleOperation {
  return typeof value === "string" && (RULE_OPERATIONS as readonly string[]).includes(value);
}

/**
 * O campo de valor **existe apenas** em `equals` e `not_equals`. Não é escondido por CSS: ele
 * não é renderizado, para que não vá junto no `FormData` e a operação sem valor continue sem
 * valor nenhum.
 */
export function RuleForm({
  applicationId,
  surveyId,
  onFinished,
}: {
  applicationId: string;
  surveyId: string;
  onFinished?: () => void;
}) {
  const [state, formAction, pending] = useActionState<FormState, FormData>(
    addSegmentationRuleAction.bind(null, applicationId, surveyId),
    idleFormState,
  );

  const [operation, setOperation] = useState<RuleOperation>("equals");

  useEffect(() => {
    if (state.status === "success") {
      onFinished?.();
    }
  }, [state, onFinished]);

  // O reset de formulário do React devolve o `Select` do Radix ao valor inicial; a operação
  // enviada volta em `values` e é reposta aqui para que a recusa não apague a escolha.
  //
  // Precisa ser efeito: o reset acontece depois da renderização (mesmo motivo documentado em
  // `questions/question-form.tsx`).
  useEffect(() => {
    if (state.status === "error" && isRuleOperation(state.values.operation)) {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- ver comentário acima
      setOperation(state.values.operation);
    }
  }, [state]);

  const values = state.status === "error" ? state.values : {};
  const errors = state.status === "error" ? state.fieldErrors : {};

  return (
    <form action={formAction} data-testid="rule-form" className="flex flex-col gap-4">
      {state.status === "error" ? <FormError message={state.message} /> : null}

      <div className="grid gap-4 sm:grid-cols-3">
        <Field>
          <FieldLabel htmlFor="attribute">Atributo</FieldLabel>
          <Input
            id="attribute"
            name="attribute"
            required
            placeholder="plano"
            defaultValue={values.attribute ?? ""}
            aria-invalid={errors.attribute !== undefined}
          />
          <FieldMessage name="attribute" errors={errors} />
        </Field>

        <Field>
          <FieldLabel htmlFor="rule-operation">Operação</FieldLabel>
          <input type="hidden" name="operation" value={operation} />
          <Select
            value={operation}
            onValueChange={(next) => setOperation(next as RuleOperation)}
          >
            <SelectTrigger id="rule-operation" data-testid="rule-operation-select">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {RULE_OPERATIONS.map((option) => (
                <SelectItem key={option} value={option}>
                  {RULE_OPERATION_LABELS[option]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </Field>

        {requiresValue(operation) ? (
          <Field>
            <FieldLabel htmlFor="rule-value">Valor</FieldLabel>
            <Input
              id="rule-value"
              name="value"
              data-testid="rule-value-input"
              required
              defaultValue={values.value ?? ""}
              aria-invalid={errors.value !== undefined}
            />
            <FieldMessage name="value" errors={errors} />
          </Field>
        ) : null}
      </div>

      <div>
        <SubmitButton pending={pending} testId="add-rule-button">
          <PlusIcon aria-hidden />
          Adicionar regra
        </SubmitButton>
      </div>
    </form>
  );
}
