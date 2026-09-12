"use client";

import { useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";

import { Button } from "@/components/ui/button";
import { Field, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

import { SDK_ERROR_KIND_LABELS } from "../lib/health-labels";
import { KIND_PARAM, SDK_VERSION_PARAM, type SdkErrorFilters } from "../schemas/filters";
import { SDK_ERROR_KINDS } from "../schemas/health";

const ALL = "all";

/** Cliente porque o recorte vive na URL: aplicar é trocar os parâmetros e deixar a página ler. */
export function SdkErrorFiltersForm({ filters }: { filters: SdkErrorFilters }) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const [kind, setKind] = useState<string>(filters.kind ?? ALL);
  const [version, setVersion] = useState(filters.sdkVersion ?? "");

  function push(next: URLSearchParams) {
    next.delete("page");
    const query = next.toString();
    router.push(query === "" ? pathname : `${pathname}?${query}`);
  }

  function apply() {
    const next = new URLSearchParams(searchParams);

    if (kind === ALL) {
      next.delete(KIND_PARAM);
    } else {
      next.set(KIND_PARAM, kind);
    }

    const trimmed = version.trim();
    if (trimmed === "") {
      next.delete(SDK_VERSION_PARAM);
    } else {
      next.set(SDK_VERSION_PARAM, trimmed);
    }

    push(next);
  }

  function clear() {
    setKind(ALL);
    setVersion("");

    const next = new URLSearchParams(searchParams);
    next.delete(KIND_PARAM);
    next.delete(SDK_VERSION_PARAM);
    push(next);
  }

  return (
    <form
      data-testid="sdk-error-filters"
      className="flex flex-wrap items-end gap-3"
      onSubmit={(event) => {
        event.preventDefault();
        apply();
      }}
    >
      <Field className="w-52">
        <FieldLabel htmlFor="filter-kind">Tipo</FieldLabel>
        <Select value={kind} onValueChange={setKind}>
          <SelectTrigger id="filter-kind" data-testid="filter-kind">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL}>Todos os tipos</SelectItem>
            {SDK_ERROR_KINDS.map((value) => (
              <SelectItem key={value} value={value}>
                {SDK_ERROR_KIND_LABELS[value]}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </Field>

      <Field className="w-40">
        <FieldLabel htmlFor="filter-sdk-version">Versão do SDK</FieldLabel>
        <Input
          id="filter-sdk-version"
          data-testid="filter-sdk-version"
          value={version}
          placeholder="1.0.0"
          maxLength={40}
          onChange={(event) => setVersion(event.target.value)}
        />
      </Field>

      <div className="flex gap-2">
        <Button type="submit" data-testid="apply-filters">
          Filtrar
        </Button>
        <Button type="button" variant="outline" data-testid="clear-filters" onClick={clear}>
          Limpar
        </Button>
      </div>
    </form>
  );
}
