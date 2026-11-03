import { z } from "zod";

import { surveyStateSchema, type SurveyState } from "./survey";

/**
 * Transições de estado.
 *
 * **Regra de ouro da interface** (FR-035): o painel não deriva as transições permitidas a
 * partir do estado. Ele lê esta lista e oferece exatamente o que vier dela. A máquina de
 * estados existe em `data-model.md` para orientar telas e testes, não para virar condicional.
 */

export const transitionReasonSchema = z.enum([
  "publication",
  "manual_pause",
  "manual_resume",
  "manual_end",
  "quota_reached",
  "window_opened",
  "window_closed",
]);

export type TransitionReason = z.infer<typeof transitionReasonSchema>;
export const TRANSITION_REASONS = transitionReasonSchema.options;

export const TRANSITION_REASON_LABELS: Record<TransitionReason, string> = {
  publication: "Publicação",
  manual_pause: "Pausada manualmente",
  manual_resume: "Retomada manualmente",
  manual_end: "Encerrada manualmente",
  quota_reached: "Encerrada por cota",
  window_opened: "Janela abriu",
  window_closed: "Janela fechou",
};

export const surveyStateTransitionSchema = z.object({
  from: surveyStateSchema,
  to: surveyStateSchema,
  reason: transitionReasonSchema,
  occurredAt: z.string(),
});

export type SurveyStateTransition = z.infer<typeof surveyStateTransitionSchema>;

/** Ações que a leitura de transições autoriza, nomeadas pelo motivo que elas produzem. */
export const MANUAL_REASONS = ["manual_pause", "manual_resume", "manual_end"] as const;
export type ManualReason = (typeof MANUAL_REASONS)[number];

export function allowedManualReasons(state: SurveyState): ManualReason[] {
  switch (state) {
    case "draft":
    case "ended":
      return [];
    case "paused":
      return ["manual_resume", "manual_end"];
    case "scheduled":
    case "active":
      return ["manual_pause", "manual_end"];
  }
}
