import type { QuestionType } from "../schemas/question";
import type { RuleOperation } from "../schemas/trigger";
import type { SurveyState } from "../schemas/survey";

/** Apresentadores puros: o vocabulário do domínio em pt-BR, testável sem renderizar nada. */

export const SURVEY_STATE_LABELS: Record<SurveyState, string> = {
  draft: "Rascunho",
  scheduled: "Agendada",
  active: "No ar",
  paused: "Pausada",
  ended: "Encerrada",
};

export const QUESTION_TYPE_LABELS: Record<QuestionType, string> = {
  single_choice: "Escolha única",
  multiple_choice: "Escolha múltipla",
  rating: "Avaliação",
  scale: "Escala",
  nps: "NPS",
  free_text: "Texto livre",
};

export const RULE_OPERATION_LABELS: Record<RuleOperation, string> = {
  equals: "é igual a",
  not_equals: "é diferente de",
  present: "está presente",
  absent: "está ausente",
};

/** Variante visual do badge por estado — a cor acompanha o significado operacional. */
export function surveyStateVariant(
  state: SurveyState,
): "default" | "secondary" | "outline" | "destructive" {
  switch (state) {
    case "active":
      return "default";
    case "scheduled":
      return "outline";
    case "paused":
      return "destructive";
    case "ended":
    case "draft":
      return "secondary";
  }
}
