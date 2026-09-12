import { notFound } from "next/navigation";

import {
  AUDIT_NOTE,
  DeletionAuditsList,
  EraseRespondentForm,
  RetentionPolicyCard,
  getRetentionPreview,
  listDeletionAudits,
} from "@/features/privacy";
import { ApiUnavailableError, parsePaginationParams } from "@/shared/api";
import { Pagination } from "@/shared/components";
import { TIMEZONE_NOTE } from "@/shared/lib";

export const metadata = { title: "Privacidade" };

/**
 * Apagar o que precisa ser apagado e guardar só o que faz sentido guardar. Casca fina: leitura
 * em paralelo da prévia de retenção e dos registros de exclusão.
 */
export default async function ApplicationPrivacyPage({
  params,
  searchParams,
}: PageProps<"/aplicacoes/[applicationId]/privacidade">) {
  const { applicationId } = await params;
  const query = await searchParams;
  const { page, size } = parsePaginationParams(query);

  const [previewResult, auditsResult] = await Promise.all([
    getRetentionPreview(applicationId),
    listDeletionAudits(applicationId, { page, size }),
  ]);

  for (const result of [previewResult, auditsResult]) {
    if (!result.ok) {
      if (result.kind === "not_found") {
        notFound();
      }
      throw new ApiUnavailableError(result);
    }
  }

  if (!previewResult.ok || !auditsResult.ok) {
    notFound();
  }

  const audits = auditsResult.data;

  return (
    <section className="flex flex-col gap-8">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h2 className="font-heading text-lg font-medium">Privacidade e retenção</h2>
        <p data-testid="timezone-note" className="text-sm text-muted-foreground">
          {TIMEZONE_NOTE}
        </p>
      </div>

      <RetentionPolicyCard applicationId={applicationId} preview={previewResult.data} />

      <section data-testid="erase-respondent" className="flex flex-col gap-3">
        <h3 className="font-heading text-base font-medium">Excluir respondente</h3>
        <p className="text-sm text-muted-foreground">
          Para atender a um pedido de exclusão que chegou pelo app. Apaga tudo que a pessoa
          respondeu nesta aplicação; em outra aplicação ela é outro respondente.
        </p>
        <EraseRespondentForm applicationId={applicationId} />
      </section>

      <section data-testid="deletion-audits" className="flex flex-col gap-3">
        <h3 className="font-heading text-base font-medium">Exclusões feitas</h3>
        <p className="text-sm text-muted-foreground">{AUDIT_NOTE}</p>
        <DeletionAuditsList audits={audits.items} />
        {audits.items.length === 0 ? null : (
          <Pagination
            pathname={`/aplicacoes/${applicationId}/privacidade`}
            searchParams={query}
            page={audits}
          />
        )}
      </section>
    </section>
  );
}
