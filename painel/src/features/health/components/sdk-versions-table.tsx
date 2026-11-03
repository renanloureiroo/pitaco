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

import { formatCount, formatShare } from "../lib/health-labels";
import type { SdkVersionUsage } from "../schemas/health";

function ShareBar({ share }: { share: number | undefined }) {
  const width = Math.round((share ?? 0) * 100);

  return (
    <div className="flex items-center gap-2">
      <div aria-hidden className="h-2 w-32 overflow-hidden rounded-full bg-muted">
        {/* Largura é dado de tempo de execução: o único estilo inline admitido. */}
        <div className="h-full bg-primary" style={{ width: `${width}%` }} />
      </div>
      <span data-testid="sdk-version-share" className="text-sm tabular-nums">
        {share === undefined ? "—" : formatShare(share)}
      </span>
    </div>
  );
}

export function SdkVersionsTable({ versions }: { versions: SdkVersionUsage[] }) {
  return (
    <Table data-testid="sdk-versions-table">
      <TableHeader>
        <TableRow>
          <TableHead>Versão</TableHead>
          <TableHead>Tráfego recente</TableHead>
          <TableHead className="text-right">Consultas recentes</TableHead>
          <TableHead className="text-right">Consultas no total</TableHead>
          <TableHead>Vista pela última vez</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {versions.map((usage) => (
          <TableRow key={usage.version} data-testid="sdk-version-row">
            <TableCell>
              <div className="flex items-center gap-2">
                <span data-testid="sdk-version" className="font-mono text-sm">
                  {usage.version}
                </span>
                {usage.stale ? (
                  <Badge variant="secondary" data-testid="sdk-version-stale">
                    Sumiu do tráfego
                  </Badge>
                ) : null}
              </div>
            </TableCell>
            <TableCell>
              <ShareBar share={usage.recentShare} />
            </TableCell>
            <TableCell className="text-right tabular-nums">
              {formatCount(usage.recentRequestCount)}
            </TableCell>
            <TableCell className="text-right tabular-nums">
              {formatCount(usage.requestCount)}
            </TableCell>
            <TableCell className="text-muted-foreground">
              {formatDateTime(usage.lastSeenAt)}
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
