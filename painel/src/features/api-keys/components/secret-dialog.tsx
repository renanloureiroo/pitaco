"use client";

import { CheckIcon, CopyIcon, TriangleAlertIcon } from "lucide-react";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";

/**
 * Exibição única do segredo (R13, SC-004).
 *
 * O segredo vive **apenas** aqui, em `useState`, vindo do estado da Server Action. Ao fechar,
 * `onClose` descarta esse estado e o valor deixa de existir no cliente. Ele nunca entra em
 * URL, `searchParams`, `localStorage`, cookie ou log — e nenhuma leitura o traz de volta,
 * porque o tipo de leitura da chave não tem o campo.
 */
export function SecretDialog({
  secret,
  label,
  onClose,
}: {
  secret: string;
  label: string;
  onClose: () => void;
}) {
  const [copied, setCopied] = useState(false);

  async function copy() {
    try {
      await navigator.clipboard.writeText(secret);
      setCopied(true);
    } catch {
      // Sem permissão de área de transferência o valor continua visível para cópia manual.
      setCopied(false);
    }
  }

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open) {
          onClose();
        }
      }}
    >
      <DialogContent data-testid="secret-dialog">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <TriangleAlertIcon aria-hidden className="size-4" />
            Copie o segredo agora
          </DialogTitle>
          <DialogDescription>
            Esta é a única vez que o segredo da chave <strong>{label}</strong> aparece. Depois de
            fechar, não há como recuperá-lo — só emitir uma nova chave.
          </DialogDescription>
        </DialogHeader>

        <code
          data-testid="secret-value"
          className="block break-all rounded-md bg-muted px-3 py-2 font-mono text-sm"
        >
          {secret}
        </code>

        <DialogFooter>
          <Button type="button" variant="outline" data-testid="copy-secret-button" onClick={copy}>
            {copied ? <CheckIcon aria-hidden /> : <CopyIcon aria-hidden />}
            {copied ? "Copiado" : "Copiar segredo"}
          </Button>
          <Button type="button" data-testid="close-secret-dialog" onClick={onClose}>
            Já copiei, fechar
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
