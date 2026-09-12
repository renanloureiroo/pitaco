import { formatDateTime } from "@/shared/lib";

import { SURVEY_STATE_LABELS } from "../../lib/survey-labels";
import {
  TRANSITION_REASON_LABELS,
  type SurveyStateTransition,
} from "../../schemas/transition";

/** Histórico do que já aconteceu com a pesquisa, na ordem em que aconteceu. */
export function TransitionsHistory({
  transitions,
}: {
  transitions: SurveyStateTransition[];
}) {
  const registered = transitions;

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
