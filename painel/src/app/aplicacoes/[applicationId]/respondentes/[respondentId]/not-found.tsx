import Link from "next/link";

import { Button } from "@/components/ui/button";

export default function RespondentNotFound() {
  return (
    <div className="flex flex-col items-start gap-4 py-10">
      <h2 className="font-heading text-xl font-semibold tracking-tight">
        Este respondente não existe
      </h2>
      <p className="text-sm text-muted-foreground">
        O identificador acessado não corresponde a nenhum respondente desta aplicação.
      </p>
      <Button asChild>
        <Link href="../">Voltar para os respondentes</Link>
      </Button>
    </div>
  );
}
