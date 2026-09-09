import { notFound } from "next/navigation";

import { VersionDetail, getVersion } from "@/features/surveys";
import { ApiUnavailableError } from "@/shared/api";

export default async function VersionPage({
  params,
}: PageProps<"/aplicacoes/[applicationId]/pesquisas/[surveyId]/versoes/[number]">) {
  const { applicationId, surveyId, number } = await params;
  const versionNumber = Number.parseInt(number, 10);

  // Número malformado na URL é o mesmo caso de versão inexistente: uma tela só.
  if (!Number.isInteger(versionNumber) || versionNumber < 1) {
    notFound();
  }

  const result = await getVersion(applicationId, surveyId, versionNumber);

  if (!result.ok) {
    if (result.kind === "not_found") {
      notFound();
    }
    throw new ApiUnavailableError(result);
  }

  return <VersionDetail version={result.data} />;
}
