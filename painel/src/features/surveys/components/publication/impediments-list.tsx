import Link from "next/link";
import { CircleAlertIcon, CircleCheckIcon } from "lucide-react";

import { impedimentMessage } from "../../lib/impediment-messages";
import type { PublicationImpediment } from "../../schemas/publication";

/**
 * Lista vazia ⇒ publicação liberada (FR-030). Quando o impedimento traz `questionKey`, o item
 * liga direto à pergunta correspondente na montagem — a pessoa vai de "o que falta" para "onde
 * resolver" em um clique.
 */
export function ImpedimentsList({
  applicationId,
  surveyId,
  impediments,
}: {
  applicationId: string;
  surveyId: string;
  impediments: PublicationImpediment[];
}) {
  if (impediments.length === 0) {
    return (
      <p
        data-testid="impediments-list"
        className="flex items-center gap-2 text-sm text-muted-foreground"
      >
        <CircleCheckIcon aria-hidden className="size-4" />
        Nada impede a publicação.
      </p>
    );
  }

  return (
    <ul data-testid="impediments-list" className="flex flex-col gap-2">
      {impediments.map((impediment, index) => (
        <li
          key={`${impediment.code}-${impediment.questionKey ?? index}`}
          data-testid="impediment-item"
          className="flex items-start gap-2 rounded-md border border-destructive/30 bg-destructive/5 px-3 py-2 text-sm"
        >
          <CircleAlertIcon aria-hidden className="mt-0.5 size-4 shrink-0 text-destructive" />
          <span>
            {impedimentMessage(impediment)}
            {impediment.questionKey !== undefined ? (
              <>
                {" "}
                <Link
                  href={`/aplicacoes/${applicationId}/pesquisas/${surveyId}#${impediment.questionKey}`}
                  className="underline underline-offset-4"
                >
                  Ir para a pergunta
                </Link>
              </>
            ) : null}
          </span>
        </li>
      ))}
    </ul>
  );
}
