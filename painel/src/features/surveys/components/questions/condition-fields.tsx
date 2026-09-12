"use client";

import { Checkbox } from "@/components/ui/checkbox";
import { Field, FieldDescription, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { FieldMessage } from "@/shared/components";
import type { FieldErrors } from "@/shared/lib";

import {
  CONDITION_OPERATOR_LABELS,
  isNumericType,
  operatorsFor,
  scaleOf,
} from "../../lib/condition";
import { CONDITION_FIELDS, type ConditionOperator } from "../../schemas/condition";
import type { Question } from "../../schemas/question";

/** O que o autor montou até aqui; números como texto, porque vêm de campo de digitação. */
export type ConditionDraft = {
  sourceKey: string;
  operator: ConditionOperator;
  values: string[];
  min: string;
  max: string;
};

export function initialConditionDraft(source: Question): ConditionDraft {
  return { sourceKey: source.key, operator: "equals", values: [], min: "", max: "" };
}

/**
 * Os campos da condição. Tudo viaja no `FormData` por campos ocultos com os nomes que a action
 * lê — os controles visíveis só editam o rascunho. A escolha de valores acompanha a origem:
 * opções da pergunta de escolha, ou a escala da pergunta numérica.
 */
export function ConditionFields({
  sources,
  draft,
  onChange,
  errors,
}: {
  sources: Question[];
  draft: ConditionDraft;
  onChange: (next: ConditionDraft) => void;
  errors: FieldErrors;
}) {
  const source = sources.find((candidate) => candidate.key === draft.sourceKey) ?? sources[0];
  const operators = operatorsFor(source.type);
  const numeric = isNumericType(source.type);
  const scale = scaleOf(source);
  const scaleValues =
    scale === undefined
      ? []
      : Array.from({ length: scale.max - scale.min + 1 }, (_, index) => String(scale.min + index));
  const choices = numeric
    ? scaleValues.map((value) => ({ value, label: value }))
    : (source.options ?? []).map((option) => ({ value: option.value, label: option.label }));

  function toggleValue(value: string, checked: boolean) {
    const values = checked
      ? [...draft.values.filter((entry) => entry !== value), value]
      : draft.values.filter((entry) => entry !== value);
    onChange({ ...draft, values });
  }

  return (
    <div data-testid="condition-fields" className="flex flex-col gap-4">
      <input type="hidden" name="conditionSourceKey" value={source.key} />
      <input type="hidden" name="conditionOperator" value={draft.operator} />
      {draft.operator === "between" ? (
        <>
          <input type="hidden" name="conditionMin" value={draft.min} />
          <input type="hidden" name="conditionMax" value={draft.max} />
        </>
      ) : (
        draft.values.map((value) => (
          <input key={value} type="hidden" name="conditionValues" value={value} />
        ))
      )}

      <div className="grid gap-4 sm:grid-cols-2">
        <Field>
          <FieldLabel htmlFor="condition-source">Pergunta de origem</FieldLabel>
          <Select
            value={source.key}
            onValueChange={(key) => {
              const next = sources.find((candidate) => candidate.key === key);
              if (next !== undefined) {
                onChange(initialConditionDraft(next));
              }
            }}
          >
            <SelectTrigger id="condition-source" data-testid="condition-source-select">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {sources.map((candidate, index) => (
                <SelectItem key={candidate.key} value={candidate.key}>
                  P{index + 1} — {candidate.statement}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <FieldMessage name={CONDITION_FIELDS.source} errors={errors} />
        </Field>

        <Field>
          <FieldLabel htmlFor="condition-operator">Quando a resposta</FieldLabel>
          <Select
            value={draft.operator}
            onValueChange={(operator) =>
              onChange({ ...draft, operator: operator as ConditionOperator, values: [], min: "", max: "" })
            }
          >
            <SelectTrigger id="condition-operator" data-testid="condition-operator-select">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {operators.map((operator) => (
                <SelectItem key={operator} value={operator}>
                  {CONDITION_OPERATOR_LABELS[operator]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <FieldMessage name={CONDITION_FIELDS.operator} errors={errors} />
        </Field>
      </div>

      {draft.operator === "between" ? (
        <div className="grid gap-4 sm:grid-cols-2">
          <Field>
            <FieldLabel htmlFor="condition-min">De</FieldLabel>
            <Input
              id="condition-min"
              data-testid="condition-min"
              inputMode="numeric"
              value={draft.min}
              onChange={(event) => onChange({ ...draft, min: event.target.value })}
            />
          </Field>
          <Field>
            <FieldLabel htmlFor="condition-max">Até</FieldLabel>
            <Input
              id="condition-max"
              data-testid="condition-max"
              inputMode="numeric"
              value={draft.max}
              onChange={(event) => onChange({ ...draft, max: event.target.value })}
            />
          </Field>
          {scale !== undefined ? (
            <FieldDescription className="sm:col-span-2">
              A escala da origem vai de {scale.min} a {scale.max}; os dois extremos entram.
            </FieldDescription>
          ) : null}
        </div>
      ) : draft.operator === "in" ? (
        <Field>
          <FieldLabel>Valores</FieldLabel>
          <div data-testid="condition-values" className="flex flex-wrap gap-4">
            {choices.map((choice) => (
              <label key={choice.value} className="flex items-center gap-2 text-sm">
                <Checkbox
                  aria-label={`Valor ${choice.label}`}
                  checked={draft.values.includes(choice.value)}
                  onCheckedChange={(checked) => toggleValue(choice.value, checked === true)}
                />
                {choice.label}
              </label>
            ))}
          </div>
        </Field>
      ) : (
        <Field>
          <FieldLabel htmlFor="condition-value">Valor</FieldLabel>
          <Select
            value={draft.values[0] ?? ""}
            onValueChange={(value) => onChange({ ...draft, values: [value] })}
          >
            <SelectTrigger id="condition-value" data-testid="condition-value-select">
              <SelectValue placeholder="Escolha um valor" />
            </SelectTrigger>
            <SelectContent>
              {choices.map((choice) => (
                <SelectItem key={choice.value} value={choice.value}>
                  {choice.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </Field>
      )}

      <FieldMessage name={CONDITION_FIELDS.values} errors={errors} />
      <FieldMessage name={CONDITION_FIELDS.whole} errors={errors} />
    </div>
  );
}
