"use client";

import { useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";

import { TERM_PARAM } from "../schemas/filters";

/** A busca vive na URL como o resto do recorte, e zera a página. */
export function OpenAnswersSearch({ term }: { term?: string }) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const [value, setValue] = useState(term ?? "");

  function push(nextTerm: string) {
    const next = new URLSearchParams(searchParams);
    next.delete("page");
    if (nextTerm.trim() === "") {
      next.delete(TERM_PARAM);
    } else {
      next.set(TERM_PARAM, nextTerm.trim());
    }
    const query = next.toString();
    router.push(query === "" ? pathname : `${pathname}?${query}`);
  }

  return (
    <form
      data-testid="open-answers-search"
      className="flex flex-wrap items-center gap-2"
      onSubmit={(event) => {
        event.preventDefault();
        push(value);
      }}
    >
      <Input
        data-testid="search-input"
        aria-label="Buscar nas respostas"
        placeholder="Buscar nas respostas"
        className="w-64"
        maxLength={200}
        value={value}
        onChange={(event) => setValue(event.target.value)}
      />
      <Button type="submit" variant="outline" data-testid="search-button">
        Buscar
      </Button>
      {term !== undefined ? (
        <Button
          type="button"
          variant="ghost"
          data-testid="clear-search"
          onClick={() => {
            setValue("");
            push("");
          }}
        >
          Limpar busca
        </Button>
      ) : null}
    </form>
  );
}
