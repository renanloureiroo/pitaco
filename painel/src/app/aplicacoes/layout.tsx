import { Suspense, type ReactNode } from "react";

import { listApplications } from "@/features/applications";
import { AppSidebar } from "@/shared/components";

/**
 * Shell do painel: sidebar + conteúdo. A lista de aplicações do seletor vem sob `<Suspense>`
 * para o corpo do layout não esperar leitura nenhuma (R6); enquanto chega, a sidebar aparece
 * sem aplicações e continua navegável.
 */
async function SidebarWithApplications() {
  const result = await listApplications({ page: 0, size: 100 });
  const applications = result.ok
    ? result.data.items.map((application) => ({
        id: application.id,
        name: application.name,
        status: application.status,
      }))
    : [];

  return <AppSidebar applications={applications} />;
}

export default function ApplicationsShellLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-dvh flex-1 flex-col lg:flex-row">
      <Suspense fallback={<AppSidebar applications={[]} />}>
        <SidebarWithApplications />
      </Suspense>
      <div className="flex min-w-0 flex-1 flex-col">{children}</div>
    </div>
  );
}
