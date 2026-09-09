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
import { formatDateTime, orNotConfigured } from "@/shared/lib";

import { CHANGE_KIND_LABELS } from "../../schemas/publication";
import type { SurveyVersion } from "../../schemas/version";

/** Da mais recente para a mais antiga: é a ordem em que se procura o que mudou. */
export function VersionsTable({
  applicationId,
  surveyId,
  versions,
}: {
  applicationId: string;
  surveyId: string;
  versions: SurveyVersion[];
}) {
  const ordered = [...versions].sort((a, b) => b.number - a.number);

  return (
    <Table data-testid="versions-table">
      <TableHeader>
        <TableRow>
          <TableHead>Versão</TableHead>
          <TableHead>Situação</TableHead>
          <TableHead>Publicada em</TableHead>
          <TableHead>Natureza da mudança</TableHead>
          <TableHead>Grupo</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {ordered.map((version) => (
          <TableRow key={version.number} data-testid="version-row">
            <TableCell>
              {version.status === "published" ? (
                <Link
                  href={`/aplicacoes/${applicationId}/pesquisas/${surveyId}/versoes/${version.number}`}
                  className="font-medium underline-offset-4 hover:underline"
                >
                  v{version.number}
                </Link>
              ) : (
                <span className="font-medium">v{version.number}</span>
              )}
            </TableCell>
            <TableCell>
              <Badge variant={version.status === "published" ? "default" : "secondary"}>
                {version.status === "published" ? "Publicada" : "Rascunho"}
              </Badge>
            </TableCell>
            <TableCell className="text-muted-foreground">
              {version.publishedAt === undefined
                ? orNotConfigured(undefined)
                : formatDateTime(version.publishedAt)}
            </TableCell>
            <TableCell className="text-muted-foreground">
              {version.changeKind === undefined
                ? orNotConfigured(undefined)
                : CHANGE_KIND_LABELS[version.changeKind]}
            </TableCell>
            <TableCell className="text-muted-foreground">{version.comparabilityGroup}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
