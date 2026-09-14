import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { formatCount, formatRate } from "../lib/results-labels";
import type { SurveyBehavior } from "../schemas/behavior";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";

export function BehaviorCard({ behavior }: { behavior: SurveyBehavior }) {
  if (!behavior.everPublished) {
    return null;
  }

  const defMap = new Map(behavior.definitions.map((d) => [d.metric, d.definition]));

  return (
    <Card data-testid="behavior-card">
      <CardHeader>
        <CardTitle>Comportamento</CardTitle>
        <p className="text-sm text-muted-foreground">
          Métricas de interação baseadas nas {formatCount(behavior.instrumented)} exibições instrumentadas neste recorte.
        </p>
      </CardHeader>
      <CardContent className="flex flex-col gap-6">
        <div className="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Pergunta</TableHead>
                <TableHead><MetricHeader title="Vistas" definition={defMap.get("viewed")} /></TableHead>
                <TableHead><MetricHeader title="Respondidas" definition={defMap.get("answered")} /></TableHead>
                <TableHead><MetricHeader title="Puladas" definition={defMap.get("skipped")} /></TableHead>
                <TableHead><MetricHeader title="Abandonadas" definition={defMap.get("abandoned")} /></TableHead>
                <TableHead><MetricHeader title="Mediana (Tempo)" definition={defMap.get("activeMsP50")} /></TableHead>
                <TableHead><MetricHeader title="Retorno" definition={defMap.get("revisitRate")} /></TableHead>
                <TableHead><MetricHeader title="Troca" definition={defMap.get("changeRate")} /></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {behavior.questions.map((q) => (
                <TableRow key={q.key}>
                  <TableCell className="font-medium max-w-xs truncate" title={q.statement}>
                    {q.position}. {q.statement}
                  </TableCell>
                  <TableCell>{formatCount(q.viewed)}</TableCell>
                  <TableCell>{formatCount(q.answered)}</TableCell>
                  <TableCell>{formatCount(q.skipped)}</TableCell>
                  <TableCell>{formatCount(q.abandoned)}</TableCell>
                  <TableCell>
                    {q.activeTime?.medianMs !== undefined ? `${(q.activeTime.medianMs / 1000).toFixed(1)}s` : "-"}
                  </TableCell>
                  <TableCell>
                    {q.revisitRate !== undefined ? formatRate(q.revisitRate) : "-"}
                  </TableCell>
                  <TableCell>
                    {q.changeRate !== undefined ? formatRate(q.changeRate) : "-"}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>

        <div className="flex flex-col gap-2">
          <h4 className="text-sm font-medium">
            <MetricHeader title="Dispensas" definition={defMap.get("dismissals")} />
          </h4>
          <dl className="grid gap-4 sm:grid-cols-6">
            {behavior.dismissals.byVia.map((via) => (
              <Stat 
                key={via.via}
                label={via.via.replace("_", " ")} 
                value={via.count} 
                share={via.share}
              />
            ))}
          </dl>
        </div>
      </CardContent>
    </Card>
  );
}

function MetricHeader({ title, definition }: { title: string; definition?: string }) {
  if (!definition) return <span>{title}</span>;
  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          <span className="underline decoration-dotted cursor-help">{title}</span>
        </TooltipTrigger>
        <TooltipContent side="top" align="center" className="max-w-xs">
          <p>{definition}</p>
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  );
}

function Stat({ label, value, share }: { label: string; value: number; share?: number }) {
  return (
    <div className="flex flex-col gap-1">
      <dt className="text-xs tracking-wide text-muted-foreground uppercase capitalize">{label}</dt>
      <dd className="text-lg font-medium">
        {formatCount(value)}
      </dd>
      {share !== undefined && (
        <span className="text-xs text-muted-foreground">{formatRate(share)}</span>
      )}
    </div>
  );
}
