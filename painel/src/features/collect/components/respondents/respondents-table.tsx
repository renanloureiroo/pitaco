import Link from "next/link";

import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatDateTime } from "@/shared/lib";

import { RESPONDENT_IDENTITY_KIND_LABELS } from "../../lib/collect-labels";
import type { Respondent } from "../../schemas/respondent";

export function RespondentsTable({
  applicationId,
  respondents,
}: {
  applicationId: string;
  respondents: Respondent[];
}) {
  return (
    <Table data-testid="respondents-table">
      <TableHeader>
        <TableRow>
          <TableHead>Forma de identificação</TableHead>
          <TableHead>Valor</TableHead>
          <TableHead>Primeiro contato</TableHead>
          <TableHead>Último contato</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {respondents.map((respondent) => (
          <TableRow key={respondent.id} data-testid="respondent-row">
            <TableCell data-testid="respondent-identity-kind">
              <Link
                href={`/aplicacoes/${applicationId}/respondentes/${respondent.id}`}
                className="font-medium underline-offset-4 hover:underline"
              >
                {RESPONDENT_IDENTITY_KIND_LABELS[respondent.identityKind]}
              </Link>
            </TableCell>
            <TableCell data-testid="respondent-identity-value" className="font-mono text-sm">
              {respondent.identityValue}
            </TableCell>
            <TableCell className="text-muted-foreground">
              {formatDateTime(respondent.firstSeenAt)}
            </TableCell>
            <TableCell className="text-muted-foreground">
              {formatDateTime(respondent.lastSeenAt)}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
