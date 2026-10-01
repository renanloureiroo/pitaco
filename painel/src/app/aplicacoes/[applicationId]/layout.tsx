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
  return (
    <div className="mx-auto flex w-full max-w-[1280px] flex-1 flex-col gap-6 px-4 py-6 sm:px-8 sm:py-8">
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

      {children}
    </div>
  );
}
