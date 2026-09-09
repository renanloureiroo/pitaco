import Link from "next/link";
import { Suspense } from "react";

import { getApplication } from "@/features/applications";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb";
import { SectionNav } from "@/shared/components";

/**
 * O corpo do layout **não** lê dado (R6): `loading.js` embrulha a página, não o layout, então
 * uma leitura bloqueante aqui atrasaria toda navegação dentro do segmento em vez de cair no
 * esqueleto. O nome da aplicação vem de um componente sob `<Suspense>`.
 */
async function ApplicationCrumb({ applicationId }: { applicationId: string }) {
  const result = await getApplication(applicationId);
  const name = result.ok ? result.data.name : "Aplicação";

  return <BreadcrumbPage>{name}</BreadcrumbPage>;
}

export default async function ApplicationLayout({
  children,
  params,
}: LayoutProps<"/aplicacoes/[applicationId]">) {
  const { applicationId } = await params;
  const base = `/aplicacoes/${applicationId}`;

  return (
    <div className="mx-auto flex w-full max-w-6xl flex-1 flex-col gap-6 px-6 py-10">
      <Breadcrumb data-testid="breadcrumb">
        <BreadcrumbList>
          <BreadcrumbItem>
            <BreadcrumbLink asChild>
              <Link href="/aplicacoes">Aplicações</Link>
            </BreadcrumbLink>
          </BreadcrumbItem>
          <BreadcrumbSeparator />
          <BreadcrumbItem>
            <Suspense fallback={<Skeleton className="h-4 w-28" />}>
              <ApplicationCrumb applicationId={applicationId} />
            </Suspense>
          </BreadcrumbItem>
        </BreadcrumbList>
      </Breadcrumb>

      <SectionNav
        items={[
          { href: base, label: "Detalhe" },
          { href: `${base}/chaves`, label: "Chaves" },
          { href: `${base}/pesquisas`, label: "Pesquisas" },
        ]}
      />

      {children}
    </div>
  );
}
