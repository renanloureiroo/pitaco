import type { ReactNode } from "react";

import {
  Empty,
  EmptyContent,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from "@/components/ui/empty";

/**
 * Listagem sem resultado é conteúdo, não falha: a tela convida à próxima ação em vez de
 * mostrar erro. Idêntico nas três listagens, então nasce compartilhado (R14).
 */
export function EmptyState({
  title,
  description,
  icon,
  action,
}: {
  title: string;
  description?: string;
  icon?: ReactNode;
  action?: ReactNode;
}) {
  return (
    <Empty data-testid="empty-state" className="border">
      <EmptyHeader>
        {icon ? <EmptyMedia variant="icon">{icon}</EmptyMedia> : null}
        <EmptyTitle>{title}</EmptyTitle>
        {description ? <EmptyDescription>{description}</EmptyDescription> : null}
      </EmptyHeader>
      {action ? <EmptyContent>{action}</EmptyContent> : null}
    </Empty>
  );
}
