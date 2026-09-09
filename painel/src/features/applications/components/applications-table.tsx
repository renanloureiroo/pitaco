import Link from "next/link";

import { Badge } from "@/components/ui/badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatDate } from "@/shared/lib";

import {
  APPLICATION_STATUS_LABELS,
  type ApplicationSummary,
} from "../schemas/application";

/** Apresentação pura: recebe a página já lida e não sabe nada sobre HTTP. */
export function ApplicationsTable({ applications }: { applications: ApplicationSummary[] }) {
  return (
    <Table data-testid="applications-table">
      <TableHeader>
        <TableRow>
          <TableHead>Nome</TableHead>
          <TableHead>Slug</TableHead>
          <TableHead>Situação</TableHead>
          <TableHead>Criada em</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {applications.map((application) => (
          <TableRow key={application.id} data-testid="application-row">
            <TableCell>
              <Link
                href={`/aplicacoes/${application.id}`}
                className="font-medium underline-offset-4 hover:underline"
              >
                {application.name}
              </Link>
            </TableCell>
            <TableCell className="font-mono text-muted-foreground">{application.slug}</TableCell>
            <TableCell>
              <Badge variant={application.status === "active" ? "default" : "secondary"}>
                {APPLICATION_STATUS_LABELS[application.status]}
              </Badge>
            </TableCell>
            <TableCell className="text-muted-foreground">
              {formatDate(application.createdAt)}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
