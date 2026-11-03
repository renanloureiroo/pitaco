"use client";

import { RotateCcwIcon } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  Empty,
  EmptyContent,
  EmptyDescription,
  EmptyHeader,
  EmptyMedia,
  EmptyTitle,
} from "@/components/ui/empty";

/**
 * Corpo de todo `error.tsx`.
 *
 * Em Next 16.3 o componente de `error.tsx` recebe `{ error, retry }` — **não** `reset`, como
 * em versões anteriores (R5). O botão chama `retry()`, que refaz a renderização do segmento.
 *
 * É Client Component porque error boundaries obrigatoriamente o são.
 */
export function ErrorState({
  title = "Não foi possível carregar esta tela",
  description,
  retry,
}: {
  title?: string;
  description?: string;
  retry: () => void;
}) {
  return (
    <Empty data-testid="error-state" className="border">
      <EmptyHeader>
        <EmptyMedia variant="icon">
          <RotateCcwIcon aria-hidden />
        </EmptyMedia>
        <EmptyTitle>{title}</EmptyTitle>
        {description ? <EmptyDescription>{description}</EmptyDescription> : null}
      </EmptyHeader>
      <EmptyContent>
        <Button type="button" data-testid="retry-button" onClick={() => retry()}>
          Tentar novamente
        </Button>
      </EmptyContent>
    </Empty>
  );
}
