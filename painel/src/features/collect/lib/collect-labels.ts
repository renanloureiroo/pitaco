import type { AnswerStatus } from "../schemas/answer";
import type { DisplayOutcome } from "../schemas/display";
import type { RespondentIdentityKind } from "../schemas/respondent";

export const DISPLAY_OUTCOME_LABELS: Record<DisplayOutcome, string> = {
  STARTED: "Em andamento",
  COMPLETED: "Concluída",
  DISMISSED: "Dispensada",
};

export function displayOutcomeVariant(
  outcome: DisplayOutcome,
): "default" | "secondary" | "outline" | "destructive" {
  switch (outcome) {
    case "COMPLETED":
      return "default";
    case "STARTED":
      return "outline";
    case "DISMISSED":
      return "secondary";
  }
}

export const RESPONDENT_IDENTITY_KIND_LABELS: Record<RespondentIdentityKind, string> = {
  APP_REFERENCE: "Referência da aplicação",
  DEVICE: "Dispositivo",
};

export const ANSWER_STATUS_LABELS: Record<Exclude<AnswerStatus, "ANSWERED">, string> = {
  SKIPPED: "Pulada",
  NOT_APPLICABLE: "Não aplicável",
  EXPIRED: "Texto expirado",
};

export const ANSWER_SKIPPED_EXPLANATION =
  "Quem respondeu viu esta pergunta e escolheu não responder.";

export const ANSWER_NOT_APPLICABLE_EXPLANATION =
  "A condição de exibição tirou esta pergunta do caminho: ela nunca foi feita a esta pessoa.";

export const ANSWER_EXPIRED_EXPLANATION =
  "Havia uma resposta em texto livre. O prazo de retenção de texto da aplicação venceu e o " +
  "conteúdo foi descartado.";

export const DISPLAY_STILL_OPEN = "ainda aberta";
export const SDK_VERSION_UNKNOWN = "não informada";
export const QUESTION_NOT_IN_VERSION = "pergunta não encontrada nesta versão";
export const ANSWER_BLANK = "sem valor registrado";
