import type { AnswerStatus } from "../schemas/answer";
import type { DisplayOutcome } from "../schemas/display";
import type { RespondentIdentityKind } from "../schemas/respondent";

/**
 * Apresentadores puros: o vocabulário da coleta em pt-BR, testável sem renderizar nada.
 *
 * Nenhum código cru do backend chega à tela (FR-020) — nem desfecho, nem forma de
 * identificação, nem situação de resposta. O que chega cru é só o **valor** da identificação,
 * que é opaco para o Pitaco e seria adulterado por qualquer tradução.
 */

export const DISPLAY_OUTCOME_LABELS: Record<DisplayOutcome, string> = {
  STARTED: "Em andamento",
  COMPLETED: "Concluída",
  DISMISSED: "Dispensada",
};

/** Variante visual do badge por desfecho — a cor acompanha o significado operacional. */
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

/**
 * Situação da resposta. `ANSWERED` não tem rótulo porque não é um estado a anunciar: o que se
 * mostra é o valor. Os outros dois **precisam** de texto próprio e distinto entre si — é a
 * regra mais fácil de quebrar desta feature (SC-006).
 */
export const ANSWER_STATUS_LABELS: Record<Exclude<AnswerStatus, "ANSWERED">, string> = {
  SKIPPED: "Pulada",
  EXPIRED: "Texto expirado",
};

export const ANSWER_SKIPPED_EXPLANATION =
  "Quem respondeu viu esta pergunta e escolheu não responder.";

export const ANSWER_EXPIRED_EXPLANATION =
  "Havia uma resposta em texto livre. O prazo de retenção de texto da aplicação venceu e o " +
  "conteúdo foi descartado.";

/** Ausências desta feature. Cada uma com texto próprio, nunca zero, data vazia ou traço (SC-005). */
export const DISPLAY_STILL_OPEN = "ainda aberta";
export const SDK_VERSION_UNKNOWN = "não informada";
export const QUESTION_NOT_IN_VERSION = "pergunta não encontrada nesta versão";
export const ANSWER_BLANK = "sem valor registrado";
