"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import {
  ActivityIcon,
  ChevronsUpDownIcon,
  KeyRoundIcon,
  LayoutDashboardIcon,
  LayoutGridIcon,
  ListChecksIcon,
  MenuIcon,
  PlusIcon,
  ShieldCheckIcon,
  UsersIcon,
  XIcon,
} from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { cn } from "@/lib/utils";

import { ThemeToggle } from "./theme-toggle";

export type SidebarApplication = { id: string; name: string; status: string };

type NavItem = { href: string; label: string; icon: ReactNode; exact?: boolean };

/** Segmentos de `/aplicacoes/...` que não são identificador de aplicação. */
const RESERVED = new Set(["nova"]);

function currentApplicationId(pathname: string): string | undefined {
  const match = /^\/aplicacoes\/([^/]+)/.exec(pathname);
  const id = match?.[1];
  return id === undefined || RESERVED.has(id) ? undefined : decodeURIComponent(id);
}

function Brand() {
  return (
    <Link href="/aplicacoes" className="flex items-center gap-2 px-2.5 py-1.5 text-base font-semibold tracking-tight">
      <span className="size-3.5 rounded-sm bg-primary" aria-hidden />
      pitaco
    </Link>
  );
}

function NavLink({ item, active, onNavigate }: { item: NavItem; active: boolean; onNavigate?: () => void }) {
  return (
    <Link
      href={item.href}
      onClick={onNavigate}
      aria-current={active ? "page" : undefined}
      className={cn(
        "flex h-9 items-center gap-2.5 rounded-md px-2.5 text-sm transition-colors [&_svg]:size-4 [&_svg]:shrink-0",
        active
          ? "bg-primary-soft font-medium text-foreground [&_svg]:text-primary-ink"
          : "text-ink-secondary hover:bg-surface-raised hover:text-foreground",
      )}
    >
      {item.icon}
      {item.label}
    </Link>
  );
}

function ApplicationSwitcher({
  applications,
  currentId,
}: {
  applications: SidebarApplication[];
  currentId: string | undefined;
}) {
  const current = applications.find((application) => application.id === currentId);

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button
          type="button"
          data-testid="application-switcher"
          className="flex h-10 w-full items-center gap-2 rounded-md border border-border-strong bg-surface px-3 text-left text-sm transition-colors hover:bg-surface-raised"
        >
          <span className="grid size-6 shrink-0 place-items-center rounded-sm bg-surface-sunken text-[11px] font-semibold uppercase">
            {(current?.name ?? "?").slice(0, 1)}
          </span>
          <span className="min-w-0 flex-1 truncate">{current?.name ?? "Escolha uma aplicação"}</span>
          <ChevronsUpDownIcon aria-hidden className="size-4 text-ink-muted" />
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="w-60">
        <DropdownMenuLabel>Aplicações</DropdownMenuLabel>
        {applications.map((application) => (
          <DropdownMenuItem key={application.id} asChild>
            <Link href={`/aplicacoes/${application.id}`} className="flex items-center gap-2">
              <span
                className={cn("size-1.5 rounded-full", application.status === "active" ? "bg-success-fill" : "bg-neutral-fill")}
                aria-hidden
              />
              <span className="truncate">{application.name}</span>
            </Link>
          </DropdownMenuItem>
        ))}
        <DropdownMenuSeparator />
        <DropdownMenuItem asChild>
          <Link href="/aplicacoes">
            <LayoutGridIcon aria-hidden />
            Todas as aplicações
          </Link>
        </DropdownMenuItem>
        <DropdownMenuItem asChild>
          <Link href="/aplicacoes/nova">
            <PlusIcon aria-hidden />
            Nova aplicação
          </Link>
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

function SidebarBody({
  applications,
  pathname,
  onNavigate,
}: {
  applications: SidebarApplication[];
  pathname: string;
  onNavigate?: () => void;
}) {
  const applicationId = currentApplicationId(pathname);
  const base = applicationId === undefined ? undefined : `/aplicacoes/${encodeURIComponent(applicationId)}`;

  const analysis: NavItem[] =
    base === undefined
      ? []
      : [
          { href: base, label: "Visão geral", icon: <LayoutDashboardIcon aria-hidden />, exact: true },
          { href: `${base}/pesquisas`, label: "Pesquisas", icon: <ListChecksIcon aria-hidden /> },
          { href: `${base}/respondentes`, label: "Respondentes", icon: <UsersIcon aria-hidden /> },
        ];
  const operation: NavItem[] =
    base === undefined
      ? []
      : [
          { href: `${base}/chaves`, label: "Chaves", icon: <KeyRoundIcon aria-hidden /> },
          { href: `${base}/saude`, label: "Saúde", icon: <ActivityIcon aria-hidden /> },
          { href: `${base}/privacidade`, label: "Privacidade", icon: <ShieldCheckIcon aria-hidden /> },
        ];
  const isActive = (item: NavItem) =>
    item.exact ? pathname === item.href : pathname === item.href || pathname.startsWith(`${item.href}/`);

  return (
    <div className="flex h-full flex-col gap-1 p-3">
      <div className="flex items-center justify-between">
        <Brand />
        <ThemeToggle />
      </div>
      <div className="my-2">
        <ApplicationSwitcher applications={applications} currentId={applicationId} />
      </div>
      {base === undefined ? (
        <nav aria-label="Navegação principal" className="flex flex-col gap-0.5">
          <NavLink
            item={{ href: "/aplicacoes", label: "Aplicações", icon: <LayoutGridIcon aria-hidden /> }}
            active={pathname === "/aplicacoes"}
            onNavigate={onNavigate}
          />
        </nav>
      ) : (
        <nav aria-label="Navegação da aplicação" className="flex flex-col gap-0.5">
          <span className="px-2.5 pt-3 pb-1 text-[11px] font-medium tracking-[0.06em] text-ink-muted uppercase">Análise</span>
          {analysis.map((item) => (
            <NavLink key={item.href} item={item} active={isActive(item)} onNavigate={onNavigate} />
          ))}
          <span className="px-2.5 pt-4 pb-1 text-[11px] font-medium tracking-[0.06em] text-ink-muted uppercase">Operação</span>
          {operation.map((item) => (
            <NavLink key={item.href} item={item} active={isActive(item)} onNavigate={onNavigate} />
          ))}
        </nav>
      )}
      <div className="mt-auto px-2.5 pt-4 text-[11px] text-ink-muted">Painel Pitaco</div>
    </div>
  );
}

/**
 * Navegação lateral do painel: marca, troca de aplicação e as seções da aplicação atual.
 * Abaixo de `lg` vira gaveta, aberta pelo botão de menu da barra superior.
 */
export function AppSidebar({ applications }: { applications: SidebarApplication[] }) {
  const pathname = usePathname();
  const [open, setOpen] = useState(false);

  useEffect(() => {
    if (!open) return;
    const onKey = (event: KeyboardEvent) => event.key === "Escape" && setOpen(false);
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open]);

  return (
    <>
      <aside className="hidden w-60 shrink-0 border-r border-border bg-sidebar lg:block" data-testid="app-sidebar">
        <div className="sticky top-0 h-dvh">
          <SidebarBody applications={applications} pathname={pathname} />
        </div>
      </aside>

      <div className="sticky top-0 z-30 flex h-14 items-center gap-2 border-b border-border bg-sidebar/95 px-4 backdrop-blur lg:hidden">
        <Button type="button" variant="ghost" size="icon-sm" aria-label="Abrir menu" onClick={() => setOpen(true)}>
          <MenuIcon aria-hidden />
        </Button>
        <Brand />
      </div>

      {open ? (
        <div className="fixed inset-0 z-40 lg:hidden" role="dialog" aria-modal="true" aria-label="Menu">
          <button type="button" aria-label="Fechar menu" className="absolute inset-0 bg-black/50" onClick={() => setOpen(false)} />
          <aside className="absolute inset-y-0 left-0 w-72 max-w-[85vw] border-r border-border bg-sidebar shadow-xl">
            <Button
              type="button"
              variant="ghost"
              size="icon-sm"
              aria-label="Fechar menu"
              className="absolute top-3 right-3 z-10"
              onClick={() => setOpen(false)}
            >
              <XIcon aria-hidden />
            </Button>
            <SidebarBody applications={applications} pathname={pathname} onNavigate={() => setOpen(false)} />
          </aside>
        </div>
      ) : null}
    </>
  );
}
