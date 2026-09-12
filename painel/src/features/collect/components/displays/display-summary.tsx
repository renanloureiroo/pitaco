import Link from "next/link";

import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { formatDateTime } from "@/shared/lib";

import {
  DISPLAY_OUTCOME_LABELS,
  DISPLAY_STILL_OPEN,
  SDK_VERSION_UNKNOWN,
  displayOutcomeVariant,
} from "../../lib/collect-labels";
import type { DisplayDetail } from "../../schemas/display";

export function DisplaySummaryCard({
  applicationId,
  display,
  surveyName,
}: {
  applicationId: string;
  display: DisplayDetail;
  surveyName?: string;
}) {
  return (
    <Card data-testid="display-summary">
      <CardHeader>
        <CardTitle className="flex flex-wrap items-center gap-3">
          Exibição da versão {display.versionNumber}
          <Badge
            data-testid="display-outcome-badge"
            variant={displayOutcomeVariant(display.outcome)}
          >
            {DISPLAY_OUTCOME_LABELS[display.outcome]}
          </Badge>
        </CardTitle>
      </CardHeader>
      <CardContent>
        <dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Item label="Pesquisa">
            <Link
              data-testid="display-survey-link"
              href={`/aplicacoes/${applicationId}/pesquisas/${display.surveyId}/exibicoes`}
              className="underline-offset-4 hover:underline"
            >
              {surveyName ?? "Ver a pesquisa"}
            </Link>
          </Item>

          <Item label="Respondente">
            <Link
              data-testid="display-respondent-link"
              href={`/aplicacoes/${applicationId}/respondentes/${display.respondentId}`}
              className="underline-offset-4 hover:underline"
            >
              Ver o histórico
            </Link>
          </Item>

          <Item label="Grupo de comparabilidade">{display.comparabilityGroup}</Item>

          <Item label="Versão do SDK">{display.sdkVersion ?? SDK_VERSION_UNKNOWN}</Item>

          <Item label="Abertura">{formatDateTime(display.openedAt)}</Item>

          <Item label="Fechamento">
            {/* Sem fechamento e sem desfecho final: ainda aberta, nunca data vazia (FR-008). */}
            {display.closedAt === undefined
              ? DISPLAY_STILL_OPEN
              : formatDateTime(display.closedAt)}
          </Item>
        </dl>
      </CardContent>
    </Card>
  );
}

function Item({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-1">
      <dt className="text-xs tracking-wide text-muted-foreground uppercase">{label}</dt>
      <dd className="text-sm">{children}</dd>
    </div>
  );
}
