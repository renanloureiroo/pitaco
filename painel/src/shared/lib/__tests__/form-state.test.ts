import { describe, expect, it } from "vitest";
import { z } from "zod";

import {
  failureFormState,
  invalidFormState,
  readFormValues,
  zodFieldErrors,
} from "../form-state";

const schema = z.object({
  name: z.string().min(1, "Informe o nome."),
  slug: z.string().regex(/^[a-z-]+$/, "Formato inválido."),
});

function zodErrorFor(input: unknown) {
  const result = schema.safeParse(input);
  if (result.success) {
    throw new Error("esperava recusa do schema");
  }
  return result.error;
}

describe("readFormValues", () => {
  it("captura o que foi digitado para devolver ao formulário", () => {
    const formData = new FormData();
    formData.set("name", "Acme");
    formData.set("slug", "Acme App");

    expect(readFormValues(formData)).toEqual({ name: "Acme", slug: "Acme App" });
  });
});

describe("zodFieldErrors", () => {
  it("mapeia a recusa do schema por campo", () => {
    expect(zodFieldErrors(zodErrorFor({ name: "", slug: "Acme App" }))).toEqual({
      name: "Informe o nome.",
      slug: "Formato inválido.",
    });
  });
});

describe("invalidFormState", () => {
  it("devolve o que foi digitado intacto junto da recusa", () => {
    const values = { name: "", slug: "Acme App" };
    const state = invalidFormState(zodErrorFor(values), values);

    expect(state).toMatchObject({ status: "error", values });
    expect(state.status === "error" && state.fieldErrors.slug).toBe("Formato inválido.");
  });
});

describe("failureFormState", () => {
  const values = { name: "Acme", slug: "acme" };

  it("transforma o 400 do backend em mensagens por campo", () => {
    const state = failureFormState(
      {
        ok: false,
        kind: "validation",
        code: "request.invalid",
        detail: "Requisição inválida.",
        errors: { slug: "Slug já em uso." },
      },
      values,
    );

    expect(state).toMatchObject({
      status: "error",
      message: "Requisição inválida.",
      fieldErrors: { slug: "Slug já em uso." },
      values,
    });
  });

  it("exibe o detail do backend sem reescrevê-lo em recusas sem campo", () => {
    const state = failureFormState(
      { ok: false, kind: "conflict", code: "application.slug_taken", detail: "Slug já em uso." },
      values,
    );

    expect(state).toMatchObject({
      status: "error",
      message: "Slug já em uso.",
      fieldErrors: {},
      values,
    });
  });

  it("explica indisponibilidade da API sem perder o formulário", () => {
    const state = failureFormState({ ok: false, kind: "unreachable" }, values);

    expect(state.status === "error" && state.message).toMatch(/não foi possível falar com a api/i);
    expect(state).toMatchObject({ values });
  });
});
