import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { NpsBreakdown } from "@/shared/components";

import { formatCount } from "../lib/results-labels";
import type { NpsSummary } from "../schemas/results";

/**
 * O NPS no topo, para pesquisa criada a partir do modelo: é a pergunta que ela existe para
 * responder. Sem resposta, o número é ausente — NPS zero seria uma afirmação sem dado.
 */
export function NpsSummaryCard({ nps }: { nps: NpsSummary }) {
  return (
    <Card data-testid="nps-summary">
      <CardHeader>
        <CardTitle className="text-[15px] font-semibold">NPS da pesquisa</CardTitle>
      </CardHeader>
      <CardContent>
        <NpsBreakdown
          nps={nps}
          scoreTestId="nps-summary-score"
          note={
            nps.respondents === 0
              ? "Ninguém respondeu a pergunta de NPS ainda."
              : `Calculado sobre ${formatCount(nps.respondents)} resposta${nps.respondents === 1 ? "" : "s"}.`
          }
        />
      </CardContent>
    </Card>
  );
}
