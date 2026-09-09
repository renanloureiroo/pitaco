import Link from "next/link";
import { ChevronLeftIcon, ChevronRightIcon } from "lucide-react";

import { Button } from "@/components/ui/button";
import type { PageResponse, RawSearchParam } from "@/shared/api";

/**
 * Paginação de listagem.
 *
 * Os links **preservam os `searchParams` vigentes** (R12): trocar de página não pode perder o
 * filtro que a pessoa aplicou, e o "voltar" do navegador precisa devolver a mesma tela.
 */

function hrefForPage(
  pathname: string,
  searchParams: Record<string, RawSearchParam>,
  page: number,
): string {
  const next = new URLSearchParams();

  for (const [key, value] of Object.entries(searchParams)) {
    if (key === "page" || value === undefined) {
      continue;
    }
    for (const single of Array.isArray(value) ? value : [value]) {
      next.append(key, single);
    }
  }

  if (page > 0) {
    next.set("page", String(page));
  }

  const query = next.toString();
  return query === "" ? pathname : `${pathname}?${query}`;
}

export function Pagination<T>({
  pathname,
  searchParams,
  page: pageData,
}: {
  pathname: string;
  searchParams: Record<string, RawSearchParam>;
  page: PageResponse<T>;
}) {
  const { page, total, totalPages } = pageData;
  const hasPrevious = page > 0;
  const hasNext = page + 1 < totalPages;

  const first = total === 0 ? 0 : page * pageData.size + 1;
  const last = total === 0 ? 0 : Math.min(total, first + pageData.items.length - 1);

  return (
    <nav
      aria-label="Paginação"
      className="flex items-center justify-between gap-4 border-t pt-4"
    >
      <p data-testid="pagination-info" className="text-sm text-muted-foreground">
        {total === 0
          ? "Nenhum resultado"
          : `${first}–${last} de ${total} · página ${page + 1} de ${totalPages}`}
      </p>

      <div className="flex items-center gap-2">
        <Button
          asChild={hasPrevious}
          variant="outline"
          size="sm"
          disabled={!hasPrevious}
          data-testid="pagination-prev"
          aria-disabled={!hasPrevious}
        >
          {hasPrevious ? (
            <Link href={hrefForPage(pathname, searchParams, page - 1)} rel="prev">
              <ChevronLeftIcon aria-hidden />
              Anterior
            </Link>
          ) : (
            <span>
              <ChevronLeftIcon aria-hidden />
              Anterior
            </span>
          )}
        </Button>

        <Button
          asChild={hasNext}
          variant="outline"
          size="sm"
          disabled={!hasNext}
          data-testid="pagination-next"
          aria-disabled={!hasNext}
        >
          {hasNext ? (
            <Link href={hrefForPage(pathname, searchParams, page + 1)} rel="next">
              Próxima
              <ChevronRightIcon aria-hidden />
            </Link>
          ) : (
            <span>
              Próxima
              <ChevronRightIcon aria-hidden />
            </span>
          )}
        </Button>
      </div>
    </nav>
  );
}
