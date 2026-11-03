import Link from "next/link";

import { Button } from "@/components/ui/button";

export default function SurveyNotFound() {
  return (
    <div className="flex flex-col items-start gap-4 py-10">
      <h1 className="font-heading text-2xl font-semibold tracking-tight">
        Esta pesquisa não existe
      </h1>
      <p className="text-sm text-muted-foreground">
        O identificador acessado não corresponde a nenhuma pesquisa desta aplicação.
      </p>
      <Button asChild>
        <Link href="../">Voltar para as pesquisas</Link>
      </Button>
    </div>
  );
}
