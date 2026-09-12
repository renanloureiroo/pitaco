"use client";

import { DownloadIcon } from "lucide-react";

import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/shared/components";

import { EXPORT_NOTICE } from "../lib/results-labels";

/**
 * O download passa pela rota do painel, que repassa o fluxo do backend. O lembrete de dado
 * pessoal vem antes: a mitigação para texto livre é de processo, não do sistema.
 */
export function ExportButton({ href }: { href: string }) {
  return (
    <ConfirmDialog
      trigger={
        <Button type="button" variant="outline" size="sm" data-testid="export-button">
          <DownloadIcon aria-hidden />
          Exportar CSV
        </Button>
      }
      title="Exportar o recorte atual em CSV"
      description={EXPORT_NOTICE}
      confirmLabel="Baixar arquivo"
      onConfirm={() => {
        window.location.assign(href);
      }}
    />
  );
}
