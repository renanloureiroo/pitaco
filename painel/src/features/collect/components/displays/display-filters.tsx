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
import { FieldMessage } from "@/shared/components";

import { DISPLAY_OUTCOME_LABELS } from "../../lib/collect-labels";
import { DISPLAY_OUTCOMES } from "../../schemas/display";
import {
  FROM_PARAM,
  OUTCOME_PARAM,
  TO_PARAM,
  VERSION_PARAM,
  periodError,
  type DisplayFilters,
} from "../../schemas/filters";

const ALL = "all";
const PERIOD_FIELD = "periodo";

/**
 * Formulário de recorte da listagem de exibições.
 *
 * `"use client"` porque escreve na URL a partir de uma interação — mesma justificativa do
 * filtro de situação de 001, e o único ponto de cliente desta feature.
 *
 * Duas regras que a tela precisa cumprir:
 *
 * - Início posterior ao fim é **recusado no campo, sem navegar** (FR-007): o que a pessoa
 *   digitou fica, e a listagem anterior permanece visível enquanto ela corrige.
 * - Trocar qualquer filtro **volta para a primeira página**: manter a página vigente mostraria
 *   uma tela vazia sempre que o novo recorte tivesse menos resultados que a página atual.
 *
 * `versions` ausente esconde o seletor de versão — é assim que o histórico do respondente
 * reusa este formulário sem oferecer um filtro que a API de lá não aceita (FR-022).
 */
export function DisplayFiltersForm({
  filters,
  versions,
}: {
  filters: DisplayFilters;
  versions?: number[];
}) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const [version, setVersion] = useState(filters.versionNumber?.toString() ?? ALL);
  const [outcome, setOutcome] = useState<string>(filters.outcome ?? ALL);
  const [from, setFrom] = useState(filters.from ?? "");
  const [to, setTo] = useState(filters.to ?? "");
  const [error, setError] = useState<string | undefined>(undefined);

  function push(next: URLSearchParams) {
    next.delete("page");
    const query = next.toString();
    router.push(query === "" ? pathname : `${pathname}?${query}`);
  }

  function apply() {
    const refusal = periodError(from, to);

    // Recusa antes de navegar: a URL só muda quando o recorte faz sentido.
    if (refusal !== undefined) {
      setError(refusal);
      return;
    }

    setError(undefined);
    const next = new URLSearchParams(searchParams);

    for (const [param, value] of [
      [VERSION_PARAM, version],
      [OUTCOME_PARAM, outcome],
      [FROM_PARAM, from],
      [TO_PARAM, to],
    ] as const) {
      if (value === "" || value === ALL) {
        next.delete(param);
      } else {
        next.set(param, value);
      }
    }

    push(next);
  }

  function clear() {
    setVersion(ALL);
    setOutcome(ALL);
    setFrom("");
    setTo("");
    setError(undefined);

    const next = new URLSearchParams(searchParams);
    for (const param of [VERSION_PARAM, OUTCOME_PARAM, FROM_PARAM, TO_PARAM]) {
      next.delete(param);
    }
    push(next);
  }

  return (
    <form
      data-testid="display-filters"
      className="flex flex-col gap-3"
      onSubmit={(event) => {
        event.preventDefault();
        apply();
      }}
    >
      <div className="flex flex-wrap items-end gap-3">
        {versions === undefined ? null : (
          <Field className="w-40">
            <FieldLabel htmlFor="filter-version">Versão</FieldLabel>
            <Select value={version} onValueChange={setVersion}>
              <SelectTrigger id="filter-version" data-testid="filter-version">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>Todas as versões</SelectItem>
                {versions.map((number) => (
                  <SelectItem key={number} value={String(number)}>
                    v{number}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </Field>
        )}

        <Field className="w-48">
          <FieldLabel htmlFor="filter-outcome">Desfecho</FieldLabel>
          <Select value={outcome} onValueChange={setOutcome}>
            <SelectTrigger id="filter-outcome" data-testid="filter-outcome">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>Todos os desfechos</SelectItem>
              {/* Exatamente os três que o backend aceita filtrar (FR-006). */}
              {DISPLAY_OUTCOMES.map((value) => (
                <SelectItem key={value} value={value}>
                  {DISPLAY_OUTCOME_LABELS[value]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </Field>

        <Field className="w-56">
          <FieldLabel htmlFor="filter-from">Aberta a partir de</FieldLabel>
          <Input
            id="filter-from"
            data-testid="filter-from"
            type="datetime-local"
            value={from}
            onChange={(event) => setFrom(event.target.value)}
          />
        </Field>

        <Field className="w-56">
          <FieldLabel htmlFor="filter-to">Aberta até</FieldLabel>
          <Input
            id="filter-to"
            data-testid="filter-to"
            type="datetime-local"
            value={to}
            onChange={(event) => setTo(event.target.value)}
          />
        </Field>

        <div className="flex items-center gap-2">
          <Button type="submit" data-testid="apply-filters">
            Aplicar
          </Button>
          <Button type="button" variant="outline" data-testid="clear-filters" onClick={clear}>
            Limpar filtros
          </Button>
        </div>
      </div>

      <FieldMessage
        name={PERIOD_FIELD}
        errors={error === undefined ? {} : { [PERIOD_FIELD]: error }}
      />
    </form>
  );
}
