import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

import { TIEBREAK_RULE } from "../../lib/warning-messages";
import type { QuotaProgress } from "../../schemas/exposure";
import { ExposureForm } from "./exposure-form";

function days(value: number): string {
  return `${value} ${value === 1 ? "dia" : "dias"}`;
}

export function describeQuietPeriod(quietPeriodDays: number | undefined): string {
  return quietPeriodDays === undefined
    ? "Sem intervalo de descanso nesta aplicação."
    : `Esta aplicação descansa ${days(quietPeriodDays)} entre pesquisas diferentes para o mesmo respondente.`;
}

export function describeQuota(progress: QuotaProgress | undefined, responseQuota?: number): string {
  const quota = progress?.responseQuota ?? responseQuota;
  if (quota === undefined) {
    return "Sem cota";
  }
  const completed = progress?.completedResponses ?? 0;
  return `Concluídas: ${completed} de ${quota}`;
}

/**
 * Quanto a pesquisa pode disputar a atenção de cada pessoa. Fica na tela de disparo porque é
 * lá que se decide quem vê e quando; a regra de desempate é dita aqui, onde a prioridade é
 * escolhida, e não só num aviso.
 */
export function ExposurePanel({
  applicationId,
  surveyId,
  priority,
  responseQuota,
  ignoresQuietPeriod,
  quietPeriodDays,
  quotaProgress,
  readOnly = false,
}: {
  applicationId: string;
  surveyId: string;
  priority: number;
  responseQuota?: number;
  ignoresQuietPeriod: boolean;
  quietPeriodDays?: number;
  quotaProgress?: QuotaProgress;
  readOnly?: boolean;
}) {
  return (
    <Card data-testid="exposure-panel">
      <CardHeader>
        <CardTitle>Exposição</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-6">
        <dl className="grid gap-4 sm:grid-cols-3">
          <div className="flex flex-col gap-1">
            <dt className="text-xs tracking-wide text-muted-foreground uppercase">Prioridade</dt>
            <dd data-testid="exposure-priority" className="text-sm">
              {priority}
            </dd>
          </div>
          <div className="flex flex-col gap-1">
            <dt className="text-xs tracking-wide text-muted-foreground uppercase">Cota</dt>
            <dd data-testid="quota-progress" className="text-sm">
              {describeQuota(quotaProgress, responseQuota)}
            </dd>
          </div>
          <div className="flex flex-col gap-1">
            <dt className="text-xs tracking-wide text-muted-foreground uppercase">Descanso</dt>
            <dd data-testid="exposure-quiet-period" className="text-sm">
              {ignoresQuietPeriod ? "Esta pesquisa ignora o intervalo" : "Respeita o intervalo"}
            </dd>
          </div>
        </dl>

        <div className="flex flex-col gap-1 text-sm text-muted-foreground">
          <p data-testid="quiet-period-policy">{describeQuietPeriod(quietPeriodDays)}</p>
          <p data-testid="tiebreak-rule">{TIEBREAK_RULE}</p>
        </div>

        {readOnly ? null : (
          <ExposureForm
            applicationId={applicationId}
            surveyId={surveyId}
            priority={priority}
            responseQuota={responseQuota}
            ignoresQuietPeriod={ignoresQuietPeriod}
          />
        )}
      </CardContent>
    </Card>
  );
}
