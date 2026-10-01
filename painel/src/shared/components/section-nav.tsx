"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

import { cn } from "@/lib/utils";

export type SectionNavItem = { href: string; label: string };

/**
 * Navegação entre as telas de um mesmo recurso (aplicação, pesquisa).
 *
 * `"use client"` só porque destacar o item atual depende de `usePathname` — é o menor
 * componente possível para essa interatividade.
 */
export function SectionNav({ items }: { items: SectionNavItem[] }) {
  const pathname = usePathname();

  return (
    <nav aria-label="Seções" className="flex gap-1 overflow-x-auto shadow-[inset_0_-1px_0_var(--border)] [scrollbar-width:none]">
      {items.map((item, index) => {
        // O primeiro item é a raiz do recurso: só ativo nela mesma. Os demais seguem ativos nas
        // subpáginas (Versões continua marcada em /versoes/1).
        const active =
          pathname === item.href || (index > 0 && pathname.startsWith(`${item.href}/`));

        return (
          <Link
            key={item.href}
            href={item.href}
            aria-current={active ? "page" : undefined}
            className={cn(
              "shrink-0 border-b-2 px-3 py-2 text-sm whitespace-nowrap transition-colors",
              active
                ? "border-primary font-medium text-foreground"
                : "border-transparent text-muted-foreground hover:text-foreground",
            )}
          >
            {item.label}
          </Link>
        );
      })}
    </nav>
  );
}
