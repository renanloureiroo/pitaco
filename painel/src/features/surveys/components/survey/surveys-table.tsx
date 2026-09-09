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
import { formatDate, orNotConfigured } from "@/shared/lib";

import { SURVEY_STATE_LABELS, surveyStateVariant } from "../../lib/survey-labels";
import type { Survey } from "../../schemas/survey";

export function SurveysTable({
  applicationId,
  surveys,
}: {
  applicationId: string;
  surveys: Survey[];
}) {
  return (
    <Table data-testid="surveys-table">
      <TableHeader>
        <TableRow>
          <TableHead>Nome</TableHead>
          <TableHead>Estado</TableHead>
          <TableHead>Versão publicada</TableHead>
          <TableHead>Criada em</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {surveys.map((survey) => (
          <TableRow key={survey.id} data-testid="survey-row">
            <TableCell>
              <Link
                href={`/aplicacoes/${applicationId}/pesquisas/${survey.id}`}
                className="font-medium underline-offset-4 hover:underline"
              >
                {survey.name}
              </Link>
            </TableCell>
            <TableCell>
              <Badge data-testid="survey-state-badge" variant={surveyStateVariant(survey.state)}>
                {SURVEY_STATE_LABELS[survey.state]}
              </Badge>
            </TableCell>
            <TableCell className="text-muted-foreground">
              {/* Nunca publicada é ausência, não a versão zero. */}
              {orNotConfigured(survey.publishedVersionNumber)}
            </TableCell>
            <TableCell className="text-muted-foreground">{formatDate(survey.createdAt)}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
