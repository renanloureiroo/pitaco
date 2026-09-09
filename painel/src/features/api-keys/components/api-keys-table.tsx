import type { ReactNode } from "react";

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

import { API_KEY_STATUS_LABELS, type ApiKey } from "../schemas/api-key";

/**
 * Exibe rótulo, prefixo, situação e datas — **nunca** o segredo, que este tipo nem carrega.
 */
export function ApiKeysTable({
  apiKeys,
  actionsFor,
}: {
  apiKeys: ApiKey[];
  actionsFor?: (apiKey: ApiKey) => ReactNode;
}) {
  return (
    <Table data-testid="api-keys-table">
      <TableHeader>
        <TableRow>
          <TableHead>Rótulo</TableHead>
          <TableHead>Prefixo</TableHead>
          <TableHead>Situação</TableHead>
          <TableHead>Emitida em</TableHead>
          <TableHead>Revogada em</TableHead>
          {actionsFor !== undefined ? <TableHead /> : null}
        </TableRow>
      </TableHeader>
      <TableBody>
        {apiKeys.map((apiKey) => (
          <TableRow key={apiKey.id} data-testid="api-key-row">
            <TableCell className="font-medium">{apiKey.label}</TableCell>
            <TableCell className="font-mono text-muted-foreground">{apiKey.prefix}</TableCell>
            <TableCell>
              <Badge variant={apiKey.status === "active" ? "default" : "secondary"}>
                {API_KEY_STATUS_LABELS[apiKey.status]}
              </Badge>
            </TableCell>
            <TableCell className="text-muted-foreground">
              {formatDateTime(apiKey.createdAt)}
            </TableCell>
            <TableCell className="text-muted-foreground">
              {apiKey.revokedAt === undefined
                ? orNotConfigured(undefined)
                : formatDateTime(apiKey.revokedAt)}
            </TableCell>
            {actionsFor !== undefined ? (
              <TableCell className="text-right">{actionsFor(apiKey)}</TableCell>
            ) : null}
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
