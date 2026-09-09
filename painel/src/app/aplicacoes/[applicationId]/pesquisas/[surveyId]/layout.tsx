import { Suspense } from "react";

import { Skeleton } from "@/components/ui/skeleton";
import { SurveyHeader } from "@/features/surveys";
import { SectionNav } from "@/shared/components";

/**
 * O corpo do layout não lê dado (R6): o cabeçalho vem de um Server Component sob `<Suspense>`,
 * para que trocar de aba dentro da pesquisa não espere pela leitura do cabeçalho.
 */
export default async function SurveyLayout({
  children,
  params,
}: LayoutProps<"/aplicacoes/[applicationId]/pesquisas/[surveyId]">) {
  const { applicationId, surveyId } = await params;
  const base = `/aplicacoes/${applicationId}/pesquisas/${surveyId}`;

  return (
    <div className="flex flex-col gap-6">
      <Suspense fallback={<Skeleton className="h-16 w-full" />}>
        <SurveyHeader applicationId={applicationId} surveyId={surveyId} />
      </Suspense>

      <SectionNav
        items={[
          { href: base, label: "Montagem" },
          { href: `${base}/disparo`, label: "Disparo" },
          { href: `${base}/publicacao`, label: "Publicação" },
          { href: `${base}/versoes`, label: "Versões" },
        ]}
      />

      {children}
    </div>
  );
}
