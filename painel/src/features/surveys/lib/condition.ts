import type { Condition, ConditionOperator } from "../schemas/condition";
import type { Question, QuestionType } from "../schemas/question";

const NUMERIC_TYPES: readonly QuestionType[] = ["rating", "scale", "nps"];

export function isNumericType(type: QuestionType): boolean {
  return NUMERIC_TYPES.includes(type);
}

/** Texto livre não é origem: não há valor com que comparar. */
export function canBeConditionSource(type: QuestionType): boolean {
  return type !== "free_text";
}

/**
 * Origens possíveis para a pergunta: as anteriores a ela, na ordem da lista. Para uma pergunta
 * nova, que entra no fim, todas as existentes são anteriores.
 */
export function conditionSources(questions: Question[], target?: Question): Question[] {
  return [...questions]
    .sort((a, b) => a.position - b.position)
    .filter((question) => target === undefined || question.position < target.position)
    .filter((question) => question.id !== target?.id)
    .filter((question) => canBeConditionSource(question.type));
}

/** Faixa só nas escalas; igual, diferente e conjunto em todas as origens. */
export function operatorsFor(type: QuestionType): ConditionOperator[] {
  return isNumericType(type)
    ? ["equals", "not_equals", "in", "between"]
    : ["equals", "not_equals", "in"];
}

export const CONDITION_OPERATOR_LABELS: Record<ConditionOperator, string> = {
  equals: "for igual a",
  not_equals: "for diferente de",
  in: "for um destes",
  between: "estiver na faixa",
};

/** A escala de onde os números da condição saem. O NPS tem a sua fixa. */
export function scaleOf(question: Question): { min: number; max: number } | undefined {
  return question.type === "nps" ? { min: 0, max: 10 } : question.range;
}

/**
 * Resumo legível da condição, como aparece na lista: "Exibida se P2 for de 0 a 6". A origem é
 * nomeada pela posição na lista, e a opção pelo rótulo que o autor escreveu.
 */
export function describeCondition(condition: Condition, questions: Question[]): string {
  const ordered = [...questions].sort((a, b) => a.position - b.position);
  const index = ordered.findIndex((question) => question.key === condition.sourceKey);
  const source = index === -1 ? undefined : ordered[index];
  const name = source === undefined ? "a pergunta de origem" : `P${index + 1}`;
  const numeric = source !== undefined && isNumericType(source.type);
  const contains = source?.type === "multiple_choice";

  const shown = (value: string) =>
    numeric
      ? value
      : `"${source?.options?.find((option) => option.value === value)?.label ?? value}"`;
  const first = condition.values[0] ?? "";

  switch (condition.operator) {
    case "between":
      return `Exibida se ${name} for de ${condition.min ?? "?"} a ${condition.max ?? "?"}`;
    case "equals":
      return contains
        ? `Exibida se ${name} incluir ${shown(first)}`
        : `Exibida se ${name} for ${shown(first)}`;
    case "not_equals":
      return contains
        ? `Exibida se ${name} não incluir ${shown(first)}`
        : `Exibida se ${name} não for ${shown(first)}`;
    case "in":
      return contains
        ? `Exibida se ${name} incluir ${condition.values.map(shown).join(" ou ")}`
        : `Exibida se ${name} for ${condition.values.map(shown).join(" ou ")}`;
  }
}
