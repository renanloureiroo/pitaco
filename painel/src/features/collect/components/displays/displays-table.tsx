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
import { formatDateTime } from "@/shared/lib";

import {
  DISPLAY_OUTCOME_LABELS,
  DISPLAY_STILL_OPEN,
  SDK_VERSION_UNKNOWN,
  displayOutcomeVariant,
} from "../../lib/collect-labels";
import type { DisplaySummary, RespondentDisplay } from "../../schemas/display";

/**
 * Uma tabela, dois eixos (R8).
 *
 * A listagem por pesquisa não mostra a coluna de pesquisa — a pesquisa é o contexto. O
 * histórico do respondente mostra, porque lá ela varia. A diferença entra como propriedade
 * explícita, e não como inferência a partir dos dados.
 *
 * A ordem vem da API (`openedAt` desc). O painel **não** reordena: reordenar no cliente
 * quebraria a paginação, que só é coerente com a ordem do servidor.
 */

type Row = DisplaySummary & { surveyId?: string };

export function DisplaysTable({
  applicationId,
  displays,
  showSurvey = false,
  surveyNames,
}: {
  applicationId: string;
  displays: DisplaySummary[] | RespondentDisplay[];
  showSurvey?: boolean;
  /** Nome por pesquisa, quando a tela já o conhece. Sem ele, o vínculo mostra o identificador. */
  surveyNames?: Record<string, string>;
}) {
  return (
    <Table data-testid="displays-table">
      <TableHeader>
        <TableRow>
          {showSurvey ? <TableHead>Pesquisa</TableHead> : null}
          <TableHead>Versão</TableHead>
          <TableHead>Grupo</TableHead>
          <TableHead>Desfecho</TableHead>
          <TableHead>SDK</TableHead>
          <TableHead>Abertura</TableHead>
          <TableHead>Fechamento</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {(displays as Row[]).map((display) => (
          <TableRow key={display.id} data-testid="display-row">
            {showSurvey ? (
              <TableCell data-testid="display-survey-cell">
                {display.surveyId === undefined ? null : (
                  <Link
                    href={`/aplicacoes/${applicationId}/pesquisas/${display.surveyId}/exibicoes`}
                    className="underline-offset-4 hover:underline"
                  >
                    {surveyNames?.[display.surveyId] ?? display.surveyId}
                  </Link>
                )}
              </TableCell>
            ) : null}
            <TableCell>
              <Link
                href={`/aplicacoes/${applicationId}/exibicoes/${display.id}`}
                className="font-medium underline-offset-4 hover:underline"
              >
                v{display.versionNumber}
              </Link>
            </TableCell>
            <TableCell className="text-muted-foreground">{display.comparabilityGroup}</TableCell>
            <TableCell>
              <Badge
                data-testid="display-outcome-badge"
                variant={displayOutcomeVariant(display.outcome)}
              >
                {DISPLAY_OUTCOME_LABELS[display.outcome]}
              </Badge>
            </TableCell>
            <TableCell className="text-muted-foreground">
              {display.sdkVersion ?? SDK_VERSION_UNKNOWN}
            </TableCell>
            <TableCell className="text-muted-foreground">
              {formatDateTime(display.openedAt)}
            </TableCell>
            <TableCell className="text-muted-foreground">
              {/* Ausente com desfecho não final é "ainda aberta" — nunca data vazia (FR-008). */}
              {display.closedAt === undefined
                ? DISPLAY_STILL_OPEN
                : formatDateTime(display.closedAt)}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
