import Link from "next/link";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { formatDateTime } from "@/shared/lib";

import { RESPONDENT_IDENTITY_KIND_LABELS } from "../../lib/collect-labels";
import type { Respondent } from "../../schemas/respondent";

export function RespondentSummary({
  applicationId,
  respondentId,
  respondent,
}: {
  applicationId: string;
  respondentId: string;
  respondent?: Respondent;
}) {
  return (
    <Card data-testid="respondent-summary">
      <CardHeader>
        <CardTitle>
          {respondent === undefined
            ? "Histórico do respondente"
            : RESPONDENT_IDENTITY_KIND_LABELS[respondent.identityKind]}
        </CardTitle>
        <p className="font-mono text-sm text-muted-foreground">
          {respondent?.identityValue ?? respondentId}
        </p>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {respondent === undefined ? (
          <p className="text-sm text-muted-foreground">
            A forma de identificação e os instantes de contato estão na{" "}
            <Link
              href={`/aplicacoes/${applicationId}/respondentes`}
              className="underline underline-offset-4"
            >
              lista de respondentes
            </Link>
            .
          </p>
        ) : (
          <dl className="grid gap-4 sm:grid-cols-2">
            <div className="flex flex-col gap-1">
              <dt className="text-xs tracking-wide text-muted-foreground uppercase">
                Primeiro contato
              </dt>
              <dd className="text-sm">{formatDateTime(respondent.firstSeenAt)}</dd>
            </div>
            <div className="flex flex-col gap-1">
              <dt className="text-xs tracking-wide text-muted-foreground uppercase">
                Último contato
              </dt>
              <dd className="text-sm">{formatDateTime(respondent.lastSeenAt)}</dd>
            </div>
          </dl>
        )}
      </CardContent>
    </Card>
  );
}
