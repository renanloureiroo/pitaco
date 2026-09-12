import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatDateTime } from "@/shared/lib";

import type { DeletionAudit } from "../schemas/privacy";

/** Quando e quanto. Não há coluna de quem, porque o backend não guarda quem. */
export function DeletionAuditsList({ audits }: { audits: DeletionAudit[] }) {
  if (audits.length === 0) {
    return (
      <p data-testid="deletion-audits-empty" className="text-sm text-muted-foreground">
        Nenhuma exclusão feita nesta aplicação.
      </p>
    );
  }

  return (
    <Table data-testid="deletion-audits-table">
      <TableHeader>
        <TableRow>
          <TableHead>Quando</TableHead>
          <TableHead className="text-right">Exibições apagadas</TableHead>
          <TableHead className="text-right">Respostas apagadas</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {audits.map((audit) => (
          <TableRow key={audit.id} data-testid="deletion-audit-row">
            <TableCell>{formatDateTime(audit.performedAt)}</TableCell>
            <TableCell data-testid="deletion-audit-displays" className="text-right">
              {audit.displaysDeleted}
            </TableCell>
            <TableCell data-testid="deletion-audit-answers" className="text-right">
              {audit.answersDeleted}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
