import { ApplicationForm } from "@/features/applications";
import { PageHeader } from "@/shared/components";

export const metadata = { title: "Nova aplicação" };

export default function NewApplicationPage() {
  return (
    <main className="mx-auto flex w-full max-w-6xl flex-1 flex-col gap-8 px-6 py-10">
      <PageHeader
        crumbs={[{ label: "Aplicações", href: "/aplicacoes" }, { label: "Nova" }]}
        title="Nova aplicação"
        description="Só o nome é obrigatório. Prazos em branco significam não configurado."
      />
      <ApplicationForm />
    </main>
  );
}
