import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { formatDateTime, formatDays } from "@/shared/lib";

import { APPLICATION_STATUS_LABELS, type Application } from "../schemas/application";

/**
 * Prazo ausente é renderizado como "não configurado", **nunca** como `0` (FR-011): a distinção
 * é o ponto desta tela, então cada prazo passa por `formatDays`, que checa ausência e não
 * veracidade.
 */
function DetailItem({
  label,
  value,
  hint,
  testId,
}: {
  label: string;
  value: string;
  hint?: string;
  testId?: string;
}) {
  return (
    <div className="flex flex-col gap-1" data-testid={testId}>
      <dt className="text-xs font-medium tracking-wide text-muted-foreground uppercase">
        {label}
      </dt>
      <dd className="text-sm">{value}</dd>
      {hint === undefined ? null : <dd className="text-xs text-muted-foreground">{hint}</dd>}
    </div>
  );
}

export function ApplicationDetail({ application }: { application: Application }) {
  return (
    <Card data-testid="application-detail">
      <CardHeader>
        <CardTitle className="flex flex-wrap items-center gap-3">
          {application.name}
          <Badge variant={application.status === "active" ? "default" : "secondary"}>
            {APPLICATION_STATUS_LABELS[application.status]}
          </Badge>
        </CardTitle>
      </CardHeader>
      <CardContent>
        <dl className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          <DetailItem label="Slug" value={application.slug} />
          <DetailItem
            label="Período de descanso"
            value={formatDays(application.quietPeriodDays)}
            hint="Vale entre pesquisas diferentes: quem viu uma não recebe outra antes do prazo."
            testId="quiet-period"
          />
          <DetailItem
            label="Retenção"
            value={formatDays(application.retentionDays)}
            testId="retention"
          />
          <DetailItem
            label="Retenção de texto livre"
            value={formatDays(application.openTextRetentionDays)}
            testId="open-text-retention"
          />
          <DetailItem label="Criada em" value={formatDateTime(application.createdAt)} />
          <DetailItem label="Atualizada em" value={formatDateTime(application.updatedAt)} />
        </dl>
      </CardContent>
    </Card>
  );
}
