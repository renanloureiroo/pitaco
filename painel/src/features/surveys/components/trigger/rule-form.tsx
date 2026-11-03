"use client";

import { PlusIcon } from "lucide-react";
import { useActionState, useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import { Field, FieldDescription, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { FieldMessage, FormError, SubmitButton } from "@/shared/components";
import { useFormStateChange } from "@/shared/hooks";
import { idleFormState, type FormState } from "@/shared/lib";

import { addSegmentationRuleAction } from "../../actions";
import { RULE_OPERATION_LABELS } from "../../lib/survey-labels";
import { RULE_OPERATIONS, requiresValue, type RuleOperation } from "../../schemas/trigger";

/** O que o app já enviou como atributo, com os valores vistos, para montar a regra sem digitar de memória. */
export type ObservedAttributeSuggestion = { name: string; values: string[] };

function isRuleOperation(value: unknown): value is RuleOperation {
  return typeof value === "string" && (RULE_OPERATIONS as readonly string[]).includes(value);
}

function Suggestions({
  label,
  items,
  selected,
  testId,
  onPick,
}: {
  label: string;
  items: string[];
  selected: string;
  testId: string;
  onPick: (item: string) => void;
}) {
  return (
    <ul className="flex flex-wrap gap-2" aria-label={label}>
      {items.map((item) => (
        <li key={item}>
          <Button
            type="button"
            variant="outline"
            size="sm"
            data-testid={testId}
            aria-pressed={selected === item}
            onClick={() => onPick(item)}
          >
            <span className="font-mono">{item}</span>
          </Button>
        </li>
      ))}
    </ul>
  );
}

/**
 * O campo de valor **existe apenas** em `equals` e `not_equals`. Não é escondido por CSS: ele
 * não é renderizado, para que não vá junto no `FormData` e a operação sem valor continue sem
 * valor nenhum.
 *
 * Atributo e valor são digitação livre com sugestão: os já vistos na aplicação viram atalhos,
 * mas a regra pode mirar um atributo que o app ainda vai começar a enviar.
 */
export function RuleForm({
  applicationId,
  surveyId,
  observedAttributes = [],
  onFinished,
}: {
  applicationId: string;
  surveyId: string;
  observedAttributes?: ObservedAttributeSuggestion[];
  onFinished?: () => void;
}) {
  const [state, formAction, pending] = useActionState<FormState, FormData>(
    addSegmentationRuleAction.bind(null, applicationId, surveyId),
    idleFormState,
  );

  const [operation, setOperation] = useState<RuleOperation>("equals");
  const [attribute, setAttribute] = useState("");
  const [value, setValue] = useState("");

  useFormStateChange(state, (next) => {
    if (next.status === "success") {
      setAttribute("");
      setValue("");
      onFinished?.();
    }
  });

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

  const errors = state.status === "error" ? state.fieldErrors : {};
  const knownValues =
    observedAttributes.find((candidate) => candidate.name === attribute.trim())?.values ?? [];

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
            value={attribute}
            onChange={(event) => setAttribute(event.target.value)}
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
              value={value}
              onChange={(event) => setValue(event.target.value)}
              aria-invalid={errors.value !== undefined}
            />
            <FieldMessage name="value" errors={errors} />
          </Field>
        ) : null}
      </div>

      <div className="flex flex-col gap-2" data-testid="observed-attributes">
        {observedAttributes.length === 0 ? (
          <p data-testid="observed-attributes-empty" className="text-sm text-muted-foreground">
            Esta aplicação ainda não enviou atributos; digite o nome que o app vai usar.
          </p>
        ) : (
          <>
            <p className="text-sm text-muted-foreground">Atributos já vistos nesta aplicação:</p>
            <Suggestions
              label="Nomes já observados"
              items={observedAttributes.map((candidate) => candidate.name)}
              selected={attribute.trim()}
              testId="observed-attribute-suggestion"
              onPick={setAttribute}
            />
          </>
        )}

        {requiresValue(operation) && knownValues.length > 0 ? (
          <>
            <p className="text-sm text-muted-foreground">Valores já vistos para este atributo:</p>
            <Suggestions
              label="Opções já observadas"
              items={knownValues}
              selected={value}
              testId="observed-value-suggestion"
              onPick={setValue}
            />
          </>
        ) : null}

        <FieldDescription>
          Atributo ausente não casa com regra que o exige. Segmente só com o necessário — nunca
          com dado pessoal.
        </FieldDescription>
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
