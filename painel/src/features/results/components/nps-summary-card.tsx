import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

import { formatCount, formatScore } from "../lib/results-labels";
import type { NpsSummary } from "../schemas/results";

/**
 * O NPS no topo, para pesquisa criada a partir do modelo: é a pergunta que ela existe para
 * responder. Sem resposta, o número é ausente — NPS zero seria uma afirmação sem dado.
 */
export function NpsSummaryCard({ nps }: { nps: NpsSummary }) {
  return (
    <Card data-testid="nps-summary">
      <CardHeader>
        <CardTitle>NPS da pesquisa</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-wrap items-center gap-8">
        <span data-testid="nps-summary-score" className="font-heading text-4xl font-semibold">
          {nps.score === undefined ? "—" : formatScore(nps.score)}
        </span>
        <dl className="grid grid-cols-3 gap-6 text-sm">
          <div className="flex flex-col gap-1">
            <dt className="text-muted-foreground">Promotores (9–10)</dt>
            <dd className="font-medium">{formatCount(nps.promoters)}</dd>
          </div>
          <div className="flex flex-col gap-1">
            <dt className="text-muted-foreground">Neutros (7–8)</dt>
            <dd className="font-medium">{formatCount(nps.passives)}</dd>
          </div>
          <div className="flex flex-col gap-1">
            <dt className="text-muted-foreground">Detratores (0–6)</dt>
            <dd className="font-medium">{formatCount(nps.detractors)}</dd>
          </div>
        </dl>
        <p className="text-sm text-muted-foreground">
          {nps.respondents === 0
            ? "Ninguém respondeu a pergunta de NPS ainda."
            : `Calculado sobre ${formatCount(nps.respondents)} resposta${nps.respondents === 1 ? "" : "s"}.`}
        </p>
      </CardContent>
    </Card>
  );
}
