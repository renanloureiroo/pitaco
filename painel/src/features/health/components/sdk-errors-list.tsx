import { Fragment } from "react";

import { Badge } from "@/components/ui/badge";
import { formatDateTime } from "@/shared/lib";

import { SDK_ERROR_KIND_LABELS, formatContextValue } from "../lib/health-labels";
import type { SdkErrorReport } from "../schemas/health";

/** O contexto fica recolhido: a lista é para achar o padrão, o detalhe é para quem investiga. */
export function SdkErrorsList({ errors }: { errors: SdkErrorReport[] }) {
  return (
    <ul data-testid="sdk-errors-list" className="flex flex-col gap-2">
      {errors.map((error) => {
        const entries = Object.entries(error.context);

        return (
          <li
            key={error.id}
            data-testid="sdk-error-item"
            className="flex flex-col gap-1 rounded-md border px-3 py-2 text-sm"
          >
            <div className="flex flex-wrap items-center gap-2">
              <Badge variant="outline" data-testid="sdk-error-kind">
                {SDK_ERROR_KIND_LABELS[error.kind]}
              </Badge>
              <span data-testid="sdk-error-version" className="font-mono text-xs text-muted-foreground">
                {error.sdkVersion ?? "versão não informada"}
              </span>
              <span className="text-xs text-muted-foreground">
                {formatDateTime(error.occurredAt)}
              </span>
            </div>
            <p data-testid="sdk-error-message" className="break-words">
              {error.message === "" ? "Sem mensagem." : error.message}
            </p>
            {entries.length === 0 ? null : (
              <details>
                <summary
                  data-testid="sdk-error-context-toggle"
                  className="cursor-pointer text-xs text-muted-foreground"
                >
                  Contexto ({entries.length})
                </summary>
                <dl
                  data-testid="sdk-error-context"
                  className="mt-1 grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 font-mono text-xs"
                >
                  {entries.map(([key, value]) => (
                    <Fragment key={key}>
                      <dt className="text-muted-foreground">{key}</dt>
                      <dd className="break-all">{formatContextValue(value)}</dd>
                    </Fragment>
                  ))}
                </dl>
              </details>
            )}
          </li>
        );
      })}
    </ul>
  );
}
