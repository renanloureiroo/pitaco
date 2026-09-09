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

/**
 * `GET .../transitions` serve a dois propósitos no contrato — dizer o que oferecer e registrar
 * o histórico — sem um campo que separe um do outro. A regra que o painel adota: uma entrada
 * cujo `from` é o **estado atual** é uma transição possível agora; as demais já aconteceram.
 *
 * Isto continua sendo o oposto de derivar do estado (FR-035): o conjunto de ações vem da lista
 * da API, e o estado só diz qual recorte dela ainda se aplica. Se a API não oferecer nada a
 * partir do estado atual, o painel não oferece nada.
 */
export function allowedManualReasons(
  transitions: SurveyStateTransition[],
  currentState: SurveyState,
): ManualReason[] {
  const offered = transitions.filter((transition) => transition.from === currentState);

  return MANUAL_REASONS.filter((reason) =>
    offered.some((transition) => transition.reason === reason),
  );
}

/** O que já aconteceu: tudo que não é uma transição possível a partir do estado atual. */
export function registeredTransitions(
  transitions: SurveyStateTransition[],
  currentState: SurveyState,
): SurveyStateTransition[] {
  return transitions.filter((transition) => transition.from !== currentState);
}
