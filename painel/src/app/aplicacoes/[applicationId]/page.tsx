import Link from "next/link";
import { notFound } from "next/navigation";
import { KeyRoundIcon, ListChecksIcon } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  ApplicationDetail,
  ApplicationStatusButton,
  EditApplicationDialog,
  getApplication,
} from "@/features/applications";
import { ApiUnavailableError } from "@/shared/api";

export default async function ApplicationPage({
  params,
}: PageProps<"/aplicacoes/[applicationId]">) {
  const { applicationId } = await params;
  const result = await getApplication(applicationId);

  if (!result.ok) {
    // 404 cobre identificador inexistente e malformado: a mesma tela serve para os dois.
    if (result.kind === "not_found") {
      notFound();
    }
    throw new ApiUnavailableError(result);
  }

  const base = `/aplicacoes/${applicationId}`;

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="font-heading text-2xl font-semibold tracking-tight">
          {result.data.name}
        </h1>
        <div className="flex flex-wrap gap-2">
          <EditApplicationDialog application={result.data} />
          <ApplicationStatusButton application={result.data} />
        </div>
      </div>

      <ApplicationDetail application={result.data} />

      <div className="flex flex-wrap gap-2">
        <Button asChild variant="outline">
          <Link href={`${base}/chaves`}>
            <KeyRoundIcon aria-hidden />
            Chaves de acesso
          </Link>
        </Button>
        <Button asChild variant="outline">
          <Link href={`${base}/pesquisas`}>
            <ListChecksIcon aria-hidden />
            Pesquisas
          </Link>
        </Button>
      </div>
    </div>
  );
}
