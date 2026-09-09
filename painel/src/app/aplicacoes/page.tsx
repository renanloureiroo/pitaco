import Link from "next/link";
import { PlusIcon, LayoutGridIcon } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  ApplicationStatusFilter,
  ApplicationsTable,
  applicationListParamsSchema,
  listApplications,
} from "@/features/applications";
import { ApiUnavailableError, parsePaginationParams } from "@/shared/api";
import { EmptyState, PageHeader, Pagination } from "@/shared/components";

export const metadata = { title: "Aplicações" };

/**
 * Casca fina: valida os `searchParams` (uma `Promise` em Next 16), pede a página à feature e
 * compõe apresentação. Nenhuma regra de negócio mora em `app/` (Princípio I).
 */
export default async function ApplicationsPage({ searchParams }: PageProps<"/aplicacoes">) {
  const params = await searchParams;
  const { page, size } = parsePaginationParams(params);
  const { status } = applicationListParamsSchema.parse(params);

  const result = await listApplications({ status, page, size });

  // Indisponibilidade e falha inesperada sobem para o error.tsx do segmento, que oferece retry.
  if (!result.ok) {
    throw new ApiUnavailableError(result);
  }

  const applications = result.data;

  return (
    <main className="mx-auto flex w-full max-w-6xl flex-1 flex-col gap-6 px-6 py-10">
      <PageHeader
        crumbs={[{ label: "Aplicações" }]}
        title="Aplicações"
        description="Cada aplicação é a raiz das suas chaves de acesso e das suas pesquisas."
        actions={
          <Button asChild data-testid="new-application-link">
            <Link href="/aplicacoes/nova">
              <PlusIcon aria-hidden />
              Nova aplicação
            </Link>
          </Button>
        }
      />

      <div className="flex flex-wrap items-center gap-3">
        <ApplicationStatusFilter status={status} />
      </div>

      {applications.items.length === 0 ? (
        <EmptyState
          icon={<LayoutGridIcon aria-hidden />}
          title={
            status === undefined
              ? "Nenhuma aplicação ainda"
              : "Nenhuma aplicação nesta situação"
          }
          description={
            status === undefined
              ? "Cadastre a primeira aplicação para emitir chaves e publicar pesquisas."
              : "Troque o filtro para ver as demais aplicações."
          }
          action={
            status === undefined ? (
              <Button asChild>
                <Link href="/aplicacoes/nova">Nova aplicação</Link>
              </Button>
            ) : undefined
          }
        />
      ) : (
        <>
          <ApplicationsTable applications={applications.items} />
          <Pagination pathname="/aplicacoes" searchParams={params} page={applications} />
        </>
      )}
    </main>
  );
}
