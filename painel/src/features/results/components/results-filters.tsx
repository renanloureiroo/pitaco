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

import {
  ABSENT_PARAM,
  ATTRIBUTE_PARAM,
  FILTER_PARAMS,
  FROM_PARAM,
  PERIOD_LABELS,
  PERIOD_PARAM,
  PERIOD_SHORTCUTS,
  TO_PARAM,
  VALUE_PARAM,
  VERSION_PARAM,
  periodError,
  type ResultsFilters,
} from "../schemas/filters";
import type { AttributeCatalog } from "../schemas/results";

const ALL = "all";
const CUSTOM = "custom";
const ABSENT = "__absent__";
const PERIOD_FIELD = "periodo";

/**
 * O recorte se aplica a agregados, taxa e respostas abertas ao mesmo tempo, porque vive na
 * URL e a página inteira lê dela (FR-05). Os atributos oferecidos vêm do que já apareceu nas
 * exibições da pesquisa — ninguém digita nome de atributo de memória.
 */
export function ResultsFiltersForm({
  filters,
  attributes,
  versions,
}: {
  filters: ResultsFilters;
  attributes: AttributeCatalog[];
  versions: number[];
}) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const initialPeriod =
    filters.period ?? (filters.from !== undefined || filters.to !== undefined ? CUSTOM : ALL);
  const [period, setPeriod] = useState<string>(initialPeriod);
  const [from, setFrom] = useState(filters.from ?? "");
  const [to, setTo] = useState(filters.to ?? "");
  const [attribute, setAttribute] = useState(filters.attribute ?? ALL);
  const [value, setValue] = useState(
    filters.attributeAbsent ? ABSENT : (filters.attributeValue ?? ALL),
  );
  const [version, setVersion] = useState(filters.versionNumber?.toString() ?? ALL);
  const [error, setError] = useState<string | undefined>(undefined);

  const catalog = attributes.find((entry) => entry.name === attribute);

  function push(next: URLSearchParams) {
    next.delete("page");
    const query = next.toString();
    router.push(query === "" ? pathname : `${pathname}?${query}`);
  }

  function apply() {
    if (period === CUSTOM) {
      const refusal = periodError(from, to);
      if (refusal !== undefined) {
        setError(refusal);
        return;
      }
    }
    setError(undefined);

    const next = new URLSearchParams(searchParams);
    for (const param of FILTER_PARAMS) {
      next.delete(param);
    }

    if (period === CUSTOM) {
      if (from !== "") {
        next.set(FROM_PARAM, from);
      }
      if (to !== "") {
        next.set(TO_PARAM, to);
      }
    } else if (period !== ALL) {
      next.set(PERIOD_PARAM, period);
    }

    if (attribute !== ALL && value !== ALL) {
      next.set(ATTRIBUTE_PARAM, attribute);
      if (value === ABSENT) {
        next.set(ABSENT_PARAM, "1");
      } else {
        next.set(VALUE_PARAM, value);
      }
    }

    if (version !== ALL) {
      next.set(VERSION_PARAM, version);
    }

    push(next);
  }

  function clear() {
    setPeriod(ALL);
    setFrom("");
    setTo("");
    setAttribute(ALL);
    setValue(ALL);
    setVersion(ALL);
    setError(undefined);

    const next = new URLSearchParams(searchParams);
    for (const param of FILTER_PARAMS) {
      next.delete(param);
    }
    push(next);
  }

  return (
    <form
      data-testid="results-filters"
      className="flex flex-col gap-3"
      onSubmit={(event) => {
        event.preventDefault();
        apply();
      }}
    >
      <div className="flex flex-wrap items-end gap-3">
        <Field className="w-48">
          <FieldLabel htmlFor="filter-period">Período</FieldLabel>
          <Select value={period} onValueChange={setPeriod}>
            <SelectTrigger id="filter-period" data-testid="filter-period">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>Todo o período</SelectItem>
              {PERIOD_SHORTCUTS.map((shortcut) => (
                <SelectItem key={shortcut} value={shortcut}>
                  {PERIOD_LABELS[shortcut]}
                </SelectItem>
              ))}
              <SelectItem value={CUSTOM}>Personalizado</SelectItem>
            </SelectContent>
          </Select>
        </Field>

        {period === CUSTOM ? (
          <>
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
          </>
        ) : null}

        {attributes.length > 0 ? (
          <>
            <Field className="w-48">
              <FieldLabel htmlFor="filter-attribute">Atributo</FieldLabel>
              <Select
                value={attribute}
                onValueChange={(next) => {
                  setAttribute(next);
                  setValue(ALL);
                }}
              >
                <SelectTrigger id="filter-attribute" data-testid="filter-attribute">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ALL}>Todos os respondentes</SelectItem>
                  {attributes.map((entry) => (
                    <SelectItem key={entry.name} value={entry.name}>
                      {entry.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </Field>

            {catalog !== undefined ? (
              <Field className="w-48">
                <FieldLabel htmlFor="filter-value">Valor</FieldLabel>
                <Select value={value} onValueChange={setValue}>
                  <SelectTrigger id="filter-value" data-testid="filter-value">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value={ALL}>Qualquer valor</SelectItem>
                    {catalog.values.map((entry) => (
                      <SelectItem key={entry.value} value={entry.value}>
                        {entry.value}
                      </SelectItem>
                    ))}
                    {/* Quem não enviou o atributo é um recorte próprio, não uma exclusão. */}
                    <SelectItem value={ABSENT}>Sem o atributo</SelectItem>
                  </SelectContent>
                </Select>
              </Field>
            ) : null}
          </>
        ) : null}

        {versions.length > 1 ? (
          <Field className="w-40">
            <FieldLabel htmlFor="filter-version">Visão</FieldLabel>
            <Select value={version} onValueChange={setVersion}>
              <SelectTrigger id="filter-version" data-testid="filter-version">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>Consolidado</SelectItem>
                {versions.map((number) => (
                  <SelectItem key={number} value={String(number)}>
                    Versão {number}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </Field>
        ) : null}

        <div className="flex items-center gap-2">
          <Button type="submit" data-testid="apply-filters">
            Aplicar
          </Button>
          <Button type="button" variant="outline" data-testid="clear-filters" onClick={clear}>
            Limpar recorte
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
