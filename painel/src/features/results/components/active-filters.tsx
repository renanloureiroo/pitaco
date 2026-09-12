import { Badge } from "@/components/ui/badge";

import { describeFilters, type ResultsFilters } from "../schemas/filters";

/** O recorte ativo fica visível junto dos números: número sem recorte visível é número mal lido. */
export function ActiveFilters({ filters }: { filters: ResultsFilters }) {
  const parts = describeFilters(filters);

  return (
    <p data-testid="active-filters" className="flex flex-wrap items-center gap-2 text-sm">
      <span className="text-muted-foreground">Recorte:</span>
      {parts.length === 0 ? (
        <span>todas as exibições</span>
      ) : (
        parts.map((part) => (
          <Badge key={part} variant="secondary" data-testid="active-filter">
            {part}
          </Badge>
        ))
      )}
    </p>
  );
}
