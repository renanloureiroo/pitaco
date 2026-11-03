"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { RefreshCwIcon } from "lucide-react";

import { Button } from "@/components/ui/button";

const INTERVAL_MS = 30_000;

/**
 * Respostas recentes entram sem recarregar a página: `router.refresh()` re-executa só o
 * Server Component. Automático a cada 30 s enquanto a aba está visível — nada de polling em
 * aba de fundo.
 */
export function RefreshControl() {
  const router = useRouter();
  const [refreshedAt, setRefreshedAt] = useState<Date | undefined>(undefined);

  function refresh() {
    router.refresh();
    setRefreshedAt(new Date());
  }

  useEffect(() => {
    const timer = setInterval(() => {
      if (document.visibilityState === "visible") {
        refresh();
      }
    }, INTERVAL_MS);
    return () => clearInterval(timer);
    // `router` é estável; o efeito só precisa nascer uma vez.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="flex items-center gap-2">
      {refreshedAt !== undefined ? (
        <span data-testid="refreshed-at" className="text-xs text-muted-foreground">
          Atualizado às {refreshedAt.toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" })}
        </span>
      ) : null}
      <Button type="button" variant="outline" size="sm" data-testid="refresh-button" onClick={refresh}>
        <RefreshCwIcon aria-hidden />
        Atualizar
      </Button>
    </div>
  );
}
