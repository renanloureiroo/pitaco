import Link from "next/link";

import { Button } from "@/components/ui/button";

export default function NotFound() {
  return (
    <main className="mx-auto flex w-full max-w-2xl flex-1 flex-col items-center justify-center gap-4 px-6 py-16 text-center">
      <h1 className="font-heading text-2xl font-semibold tracking-tight">
        Esta página não existe
      </h1>
      <p className="text-sm text-muted-foreground">
        O endereço acessado não corresponde a nenhuma tela do painel.
      </p>
      <Button asChild>
        <Link href="/aplicacoes">Ir para aplicações</Link>
      </Button>
    </main>
  );
}
