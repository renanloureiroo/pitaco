import Link from "next/link";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

import {
  describeRetentionPolicy,
  lastRunMessage,
  retentionWarning,
} from "../lib/privacy-labels";
import type { RetentionPreview } from "../schemas/privacy";

/**
 * A política vigente e o aviso antes do descarte. Os prazos são editados no detalhe da
 * aplicação; aqui fica o que eles vão fazer e quando.
 */
export function RetentionPolicyCard({
  applicationId,
  preview,
}: {
  applicationId: string;
  preview: RetentionPreview;
}) {
  const warning = retentionWarning(preview);
  const base = `/aplicacoes/${applicationId}`;

  return (
    <Card data-testid="retention-policy">
      <CardHeader>
        <CardTitle>Retenção</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <p data-testid="retention-policy-description" className="text-sm">
          {describeRetentionPolicy(preview)}
        </p>

        {warning === undefined ? null : (
          <p
            data-testid="retention-warning"
            role="alert"
            className="rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive"
          >
            {warning}
          </p>
        )}

        {preview.configured ? (
          <p data-testid="retention-last-run" className="text-sm text-muted-foreground">
            {lastRunMessage(preview)}
          </p>
        ) : null}

        <div className="flex flex-wrap gap-2">
          <Button asChild variant="outline">
            <Link href={base}>Editar prazos</Link>
          </Button>
          {warning === undefined ? null : (
            <Button asChild variant="outline">
              <Link href={`${base}/pesquisas`}>Exportar pelos resultados das pesquisas</Link>
            </Button>
          )}
        </div>
      </CardContent>
    </Card>
  );
}
