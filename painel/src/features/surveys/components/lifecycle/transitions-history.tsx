import { formatDateTime } from "@/shared/lib";

import { SURVEY_STATE_LABELS } from "../../lib/survey-labels";
import type { SurveyState } from "../../schemas/survey";
import {
  TRANSITION_REASON_LABELS,
  registeredTransitions,
  type SurveyStateTransition,
} from "../../schemas/transition";

/**
 * Histórico do que já aconteceu com a pesquisa.
 *
 * A mesma leitura serve a dois propósitos no contrato: autorizar ações e registrar histórico.
 * Aqui só o registro importa — as entradas que partem do estado atual são ações ainda
 * possíveis, não fatos, e por isso ficam de fora.
 */
export function TransitionsHistory({
  transitions,
  currentState,
}: {
  transitions: SurveyStateTransition[];
  currentState: SurveyState;
}) {
  const registered = registeredTransitions(transitions, currentState);

  if (registered.length === 0) {
    return null;
  }

  return (
    <ol data-testid="transitions-history" className="flex flex-col gap-2">
      {registered.map((transition, index) => (
        <li
          key={`${transition.occurredAt}-${index}`}
          className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground"
        >
          <span className="text-foreground">{TRANSITION_REASON_LABELS[transition.reason]}</span>
          <span>
            {SURVEY_STATE_LABELS[transition.from]} → {SURVEY_STATE_LABELS[transition.to]}
          </span>
          <span>{formatDateTime(transition.occurredAt)}</span>
        </li>
      ))}
    </ol>
  );
}
