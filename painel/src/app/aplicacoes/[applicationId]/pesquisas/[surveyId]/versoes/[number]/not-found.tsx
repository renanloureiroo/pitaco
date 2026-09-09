import Link from "next/link";

import { Button } from "@/components/ui/button";

export default function VersionNotFound() {
  return (
    <div className="flex flex-col items-start gap-4 py-10">
      <h2 className="font-heading text-xl font-semibold tracking-tight">
        Esta versão não existe
      </h2>
      <p className="text-sm text-muted-foreground">
        O número acessado não corresponde a nenhuma versão desta pesquisa.
      </p>
      <Button asChild>
        <Link href="../">Voltar para as versões</Link>
      </Button>
    </div>
  );
}
