"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";

import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

import {
  APPLICATION_STATUS_LABELS,
  type ApplicationStatus,
} from "../schemas/application";

const ALL = "all";

/**
 * `"use client"` porque escreve na URL a partir de uma interação.
 *
 * Trocar o filtro **volta para a primeira página**: manter a página vigente mostraria uma
 * tela vazia sempre que o novo filtro tivesse menos resultados que a página atual.
 */
export function ApplicationStatusFilter({ status }: { status?: ApplicationStatus }) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  function apply(value: string) {
    const next = new URLSearchParams(searchParams);

    if (value === ALL) {
      next.delete("status");
    } else {
      next.set("status", value);
    }
    next.delete("page");

    const query = next.toString();
    router.push(query === "" ? pathname : `${pathname}?${query}`);
  }

  return (
    <Select value={status ?? ALL} onValueChange={apply}>
      <SelectTrigger
        data-testid="application-status-filter"
        aria-label="Filtrar por situação"
        className="w-44"
      >
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value={ALL}>Todas as situações</SelectItem>
        <SelectItem value="active">{APPLICATION_STATUS_LABELS.active}</SelectItem>
        <SelectItem value="inactive">{APPLICATION_STATUS_LABELS.inactive}</SelectItem>
      </SelectContent>
    </Select>
  );
}
