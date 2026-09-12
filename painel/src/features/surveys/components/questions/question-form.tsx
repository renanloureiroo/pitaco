"use client";

import { PlusIcon, XIcon } from "lucide-react";
import { useActionState, useEffect, useRef, useState } from "react";

import { Button } from "@/components/ui/button";
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
import { Textarea } from "@/components/ui/textarea";
import { FieldMessage, FormError, SubmitButton } from "@/shared/components";
import { idleFormState, type FormState } from "@/shared/lib";

import { addQuestionAction, updateQuestionAction } from "../../actions";
import { conditionSources } from "../../lib/condition";
import { QUESTION_TYPE_LABELS } from "../../lib/survey-labels";
import {
  QUESTION_TYPES,
  acceptsLabels,
  acceptsRange,
  isQuestionType,
  requiresOptions,
  type Question,
  type QuestionType,
} from "../../schemas/question";
import { ConditionFields, initialConditionDraft, type ConditionDraft } from "./condition-fields";

type DraftOption = { label: string; value: string };

function draftOf(question: Question | undefined): ConditionDraft | undefined {
  const condition = question?.condition;
  if (condition === undefined) {
    return undefined;
  }
  return {
    sourceKey: condition.sourceKey,
    operator: condition.operator,
    values: condition.values,
    min: condition.min === undefined ? "" : String(condition.min),
    max: condition.max === undefined ? "" : String(condition.max),
  };
}

/**
 * `"use client"` porque a lista de opções é editada localmente e os campos aparecem conforme o
 * tipo escolhido — interatividade que não existe no servidor.
 *
 * Os campos de opção só existem para tipos de escolha, e a faixa só para `rating`/`scale`: um
 * campo que não pertence ao tipo não é escondido por CSS, ele não é renderizado, para não ir
 * junto no `FormData`. A condição segue a mesma regra: desligada, nenhum campo dela vai.
 */
export function QuestionForm({
  applicationId,
  surveyId,
  question,
  questions = [],
  onFinished,
}: {
  applicationId: string;
  surveyId: string;
  question?: Question;
  /** As perguntas da versão, de onde saem as origens possíveis da condição. */
  questions?: Question[];
  onFinished?: () => void;
}) {
  const action =
    question === undefined
      ? addQuestionAction.bind(null, applicationId, surveyId)
      : updateQuestionAction.bind(null, applicationId, surveyId, question.id);

  const [state, formAction, pending] = useActionState<FormState, FormData>(action, idleFormState);

  const [type, setType] = useState<QuestionType>(question?.type ?? "free_text");
  const [options, setOptions] = useState<DraftOption[]>(
    question?.options?.map((option) => ({ label: option.label, value: option.value })) ?? [
      { label: "", value: "" },
    ],
  );
  const [condition, setCondition] = useState<ConditionDraft | undefined>(draftOf(question));
  const submitted = useRef<ConditionDraft | undefined>(undefined);

  const sources = conditionSources(questions, question);

  useEffect(() => {
    if (state.status === "success") {
      onFinished?.();
    }
  }, [state, onFinished]);

  // Depois de uma action, o React reseta o formulário; os `Select` e `Checkbox` do Radix escutam
  // esse reset e voltam ao valor inicial. O tipo volta de `values`; a condição volta do que foi
  // enviado, guardado no submit — `values` só guarda o último de cada campo repetido.
  //
  // Precisa ser efeito, e não ajuste durante o render: o reset do Radix acontece **depois** da
  // renderização, então uma correção feita no render seria desfeita logo em seguida.
  useEffect(() => {
    if (state.status === "error") {
      if (isQuestionType(state.values.type)) {
        // eslint-disable-next-line react-hooks/set-state-in-effect -- ver comentário acima
        setType(state.values.type);
      }
      setCondition(submitted.current);
    }
  }, [state]);

  const errors = state.status === "error" ? state.fieldErrors : {};
  const values = state.status === "error" ? state.values : {};

  function updateOption(index: number, patch: Partial<DraftOption>) {
    setOptions((current) =>
      current.map((option, position) => (position === index ? { ...option, ...patch } : option)),
    );
  }

  return (
    <form
      action={formAction}
      onSubmit={() => {
        submitted.current = condition;
      }}
      data-testid="question-form"
      className="flex flex-col gap-6"
    >
      {state.status === "error" ? <FormError message={state.message} /> : null}

      <Field>
        <FieldLabel htmlFor="statement">Enunciado</FieldLabel>
        <Textarea
          id="statement"
          name="statement"
          required
          rows={2}
          defaultValue={values.statement ?? question?.statement ?? ""}
          aria-invalid={errors.statement !== undefined}
        />
        <FieldMessage name="statement" errors={errors} />
      </Field>

      <Field>
        <FieldLabel htmlFor="question-type">Tipo</FieldLabel>
        <input type="hidden" name="type" value={type} />
        <Select value={type} onValueChange={(next) => setType(next as QuestionType)}>
          <SelectTrigger id="question-type" data-testid="question-type-select">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {QUESTION_TYPES.map((option) => (
              <SelectItem key={option} value={option}>
                {QUESTION_TYPE_LABELS[option]}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <FieldMessage name="type" errors={errors} />
      </Field>

      <Field orientation="horizontal">
        <Checkbox id="required" name="required" defaultChecked={question?.required ?? true} />
        <FieldLabel htmlFor="required">Resposta obrigatória</FieldLabel>
      </Field>

      {requiresOptions(type) ? (
        <Field data-testid="question-options">
          <FieldLabel>Opções</FieldLabel>
          <FieldDescription>
            Pelo menos uma. O valor em branco assume o texto do rótulo.
          </FieldDescription>

          <div className="flex flex-col gap-2">
            {options.map((option, index) => (
              <div key={index} className="flex items-center gap-2">
                <Input
                  name="optionLabels"
                  aria-label={`Rótulo da opção ${index + 1}`}
                  placeholder="Rótulo"
                  value={option.label}
                  onChange={(event) => updateOption(index, { label: event.target.value })}
                />
                <Input
                  name="optionValues"
                  aria-label={`Valor da opção ${index + 1}`}
                  placeholder="Valor"
                  value={option.value}
                  onChange={(event) => updateOption(index, { value: event.target.value })}
                />
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  aria-label={`Remover opção ${index + 1}`}
                  disabled={options.length === 1}
                  onClick={() =>
                    setOptions((current) => current.filter((_, position) => position !== index))
                  }
                >
                  <XIcon aria-hidden />
                </Button>
              </div>
            ))}
          </div>

          <div>
            <Button
              type="button"
              variant="outline"
              size="sm"
              data-testid="add-option-button"
              onClick={() => setOptions((current) => [...current, { label: "", value: "" }])}
            >
              <PlusIcon aria-hidden />
              Adicionar opção
            </Button>
          </div>

          <FieldMessage name="options" errors={errors} />
        </Field>
      ) : null}

      {acceptsRange(type) ? (
        <div data-testid="question-range" className="grid gap-4 sm:grid-cols-2">
          <Field>
            <FieldLabel htmlFor="rangeMin">Mínimo</FieldLabel>
            <Input
              id="rangeMin"
              name="rangeMin"
              inputMode="numeric"
              defaultValue={values.rangeMin ?? question?.range?.min ?? ""}
            />
            <FieldMessage name="rangeMin" errors={errors} />
          </Field>
          <Field>
            <FieldLabel htmlFor="rangeMax">Máximo</FieldLabel>
            <Input
              id="rangeMax"
              name="rangeMax"
              inputMode="numeric"
              defaultValue={values.rangeMax ?? question?.range?.max ?? ""}
            />
            <FieldMessage name="rangeMax" errors={errors} />
          </Field>
        </div>
      ) : null}

      {acceptsLabels(type) ? (
        <div data-testid="question-labels" className="grid gap-4 sm:grid-cols-2">
          <Field>
            <FieldLabel htmlFor="rangeMinLabel">Rótulo do menor valor</FieldLabel>
            <Input
              id="rangeMinLabel"
              name="rangeMinLabel"
              maxLength={60}
              placeholder="Opcional"
              defaultValue={values.rangeMinLabel ?? question?.range?.minLabel ?? ""}
            />
            <FieldMessage name="rangeMinLabel" errors={errors} />
          </Field>
          <Field>
            <FieldLabel htmlFor="rangeMaxLabel">Rótulo do maior valor</FieldLabel>
            <Input
              id="rangeMaxLabel"
              name="rangeMaxLabel"
              maxLength={60}
              placeholder="Opcional"
              defaultValue={values.rangeMaxLabel ?? question?.range?.maxLabel ?? ""}
            />
            <FieldMessage name="rangeMaxLabel" errors={errors} />
          </Field>
        </div>
      ) : null}

      <fieldset
        data-testid="question-condition-section"
        className="flex flex-col gap-4 rounded-md border p-4"
      >
        <legend className="px-1 text-sm font-medium">Exibir somente se…</legend>
        {sources.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            Nenhuma pergunta anterior pode servir de origem. A condição olha para uma pergunta que
            vem antes desta, e texto livre não serve.
          </p>
        ) : (
          <>
            <Field orientation="horizontal">
              <Checkbox
                id="conditioned"
                data-testid="condition-toggle"
                checked={condition !== undefined}
                onCheckedChange={(checked) =>
                  setCondition(checked === true ? initialConditionDraft(sources[0]) : undefined)
                }
              />
              <FieldLabel htmlFor="conditioned">
                Mostrar só para quem respondeu de um jeito uma pergunta anterior
              </FieldLabel>
            </Field>
            {condition !== undefined ? (
              <ConditionFields
                sources={sources}
                draft={condition}
                onChange={setCondition}
                errors={errors}
              />
            ) : null}
          </>
        )}
      </fieldset>

      <div className="flex items-center gap-2">
        <SubmitButton pending={pending}>
          {question === undefined ? "Adicionar pergunta" : "Salvar pergunta"}
        </SubmitButton>
        {onFinished !== undefined ? (
          <Button type="button" variant="ghost" onClick={onFinished}>
            Cancelar
          </Button>
        ) : null}
      </div>
    </form>
  );
}
