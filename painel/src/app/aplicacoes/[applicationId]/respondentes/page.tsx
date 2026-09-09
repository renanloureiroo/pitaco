import { notFound } from "next/navigation";

import { RespondentsTable, listRespondents } from "@/features/collect";
import { ApiUnavailableError, parsePaginationParams } from "@/shared/api";
import { EmptyState, Pagination } from "@/shared/components";
import { TIMEZONE_NOTE } from "@/shared/lib";

export const metadata = { title: "Respondentes" };

/** Uma leitura só: a listagem é o conteúdo, e não há número a produzir fora dela (SC-009). */
export default async function RespondentsPage({
  params,
  searchParams,
}: PageProps<"/aplicacoes/[applicationId]/respondentes">) {
  const { applicationId } = await params;
  const query = await searchParams;
  const { page, size } = parsePaginationParams(query);

  const result = await listRespondents(applicationId, { page, size });

  if (!result.ok) {
    if (result.kind === "not_found") {
      notFound();
    }
    throw new ApiUnavailableError(result);
  }

  const respondents = result.data;

  return (
    <section className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h2 className="font-heading text-lg font-medium">Respondentes</h2>
        <p data-testid="timezone-note" className="text-sm text-muted-foreground">
          {TIMEZONE_NOTE}
        </p>
      </div>

      {respondents.items.length === 0 ? (
        // Aplicação sem contato é convite, nunca erro: não há filtro aqui que possa ter
        // esvaziado a lista.
        <EmptyState
          title="Esta aplicação ainda não recebeu contato"
          description="Quem for visto pelo SDK aparece aqui, com a identificação que a aplicação informar."
        />
      ) : (
        <>
          <RespondentsTable applicationId={applicationId} respondents={respondents.items} />
          <Pagination
            pathname={`/aplicacoes/${applicationId}/respondentes`}
            searchParams={query}
            page={respondents}
          />
        </>
      )}
    </section>
  );
}
