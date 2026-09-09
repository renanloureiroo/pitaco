import Link from "next/link";
import { Fragment, type ReactNode } from "react";

import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb";

export type Crumb = { label: ReactNode; href?: string };

/** Cabeçalho com trilha de navegação, título e ações. Presente em todas as telas. */
export function PageHeader({
  crumbs,
  title,
  description,
  actions,
}: {
  crumbs: Crumb[];
  title: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <header data-testid="page-header" className="flex flex-col gap-4">
      {crumbs.length > 0 ? (
        <Breadcrumb data-testid="breadcrumb">
          <BreadcrumbList>
            {crumbs.map((crumb, index) => (
              <Fragment key={index}>
                {index > 0 ? <BreadcrumbSeparator /> : null}
                <BreadcrumbItem>
                  {crumb.href === undefined ? (
                    <BreadcrumbPage>{crumb.label}</BreadcrumbPage>
                  ) : (
                    <BreadcrumbLink asChild>
                      <Link href={crumb.href}>{crumb.label}</Link>
                    </BreadcrumbLink>
                  )}
                </BreadcrumbItem>
              </Fragment>
            ))}
          </BreadcrumbList>
        </Breadcrumb>
      ) : null}

      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex flex-col gap-1">
          <h1 className="font-heading text-2xl font-semibold tracking-tight">{title}</h1>
          {description ? (
            <p className="text-sm text-muted-foreground">{description}</p>
          ) : null}
        </div>
        {actions ? <div className="flex items-center gap-2">{actions}</div> : null}
      </div>
    </header>
  );
}
