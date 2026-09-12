import Link from "next/link";
import { TriangleAlertIcon } from "lucide-react";

import { TIEBREAK_RULE, warningMessage } from "../../lib/warning-messages";
import type { PublicationWarning } from "../../schemas/warnings";

/**
 * Avisos não impedem nada: a publicação continua liberada. O que eles fazem é tornar visível a
 * escolha que, sem eles, só apareceria uma semana depois num resultado vazio.
 */
export function WarningsList({
  applicationId,
  warnings,
}: {
  applicationId: string;
  warnings: PublicationWarning[];
}) {
  if (warnings.length === 0) {
    return null;
  }

  return (
    <ul data-testid="warnings-list" className="flex flex-col gap-2">
      {warnings.map((warning, index) => (
        <li
          key={`${warning.code}-${warning.ruleId ?? warning.attribute ?? index}`}
          data-testid="warning-item"
          data-code={warning.code}
          className="flex items-start gap-2 rounded-md border border-amber-500/40 bg-amber-500/5 px-3 py-2 text-sm"
        >
          <TriangleAlertIcon aria-hidden className="mt-0.5 size-4 shrink-0 text-amber-600" />
          <div className="flex flex-col gap-1">
            <span>{warningMessage(warning)}</span>
            {warning.competingSurveys.length > 0 ? (
              <>
                <ul className="flex flex-col gap-1" aria-label="Pesquisas concorrentes">
                  {warning.competingSurveys.map((survey) => (
                    <li key={survey.surveyId} data-testid="competing-survey">
                      <Link
                        href={`/aplicacoes/${applicationId}/pesquisas/${survey.surveyId}/disparo`}
                        className="underline underline-offset-4"
                      >
                        {survey.name}
                      </Link>{" "}
                      <span className="text-muted-foreground">(prioridade {survey.priority})</span>
                    </li>
                  ))}
                </ul>
                <span className="text-muted-foreground">{TIEBREAK_RULE}</span>
              </>
            ) : null}
          </div>
        </li>
      ))}
    </ul>
  );
}
