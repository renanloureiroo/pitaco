import { describe, expect, it } from "vitest";

import {
  ApiUnavailableError,
  describeFailure,
  statusToKind,
  toApiFailure,
  unreachableFailure,
} from "../errors";

const problemBody = {
  type: "https://pitaco.dev/errors/application-not-found",
  title: "Aplicação não encontrada",
  status: 404,
  detail: "Nenhuma aplicação com o identificador informado.",
  code: "application.not_found",
  traceId: "trace-123",
};

describe("statusToKind", () => {
  it.each([
    [400, "validation"],
    [403, "forbidden"],
    [404, "not_found"],
    [409, "conflict"],
    [401, "unknown"],
    [422, "unknown"],
    [500, "unknown"],
    [503, "unknown"],
  ])("traduz %i em %s", (status, kind) => {
    expect(statusToKind(status)).toBe(kind);
  });
});

describe("toApiFailure", () => {
  it("preserva code, detail e traceId do corpo RFC 9457", () => {
    const failure = toApiFailure(404, problemBody);

    expect(failure).toEqual({
      ok: false,
      kind: "not_found",
      code: "application.not_found",
      detail: "Nenhuma aplicação com o identificador informado.",
      traceId: "trace-123",
    });
  });

  it("expõe as mensagens por campo de um 400 ApiValidationError", () => {
    const failure = toApiFailure(400, {
      status: 400,
      detail: "Requisição inválida.",
      code: "request.invalid",
      errors: { slug: "Formato inválido.", name: "Obrigatório." },
    });

    expect(failure).toMatchObject({
      kind: "validation",
      errors: { slug: "Formato inválido.", name: "Obrigatório." },
    });
  });

  it("dá a um 400 sem errors um mapa vazio, nunca undefined", () => {
    const failure = toApiFailure(400, { code: "request.invalid", detail: "Inválida." });

    expect(failure).toMatchObject({ kind: "validation", errors: {} });
  });

  it("trata 403 api_key.forbidden_surface como recusa nomeada", () => {
    const failure = toApiFailure(403, {
      code: "api_key.forbidden_surface",
      detail: "Chave de aplicação não pode acessar a superfície administrativa.",
    });

    expect(failure).toMatchObject({ kind: "forbidden", code: "api_key.forbidden_surface" });
  });

  it("cai em texto genérico quando o corpo está ausente", () => {
    const failure = toApiFailure(409, undefined);

    expect(failure).toMatchObject({ kind: "conflict", code: "unknown" });
    expect(describeFailure(failure)).toMatch(/recusou a requisição/i);
  });

  it("não quebra com corpo malformado e ignora campos de tipo errado", () => {
    const failure = toApiFailure(500, { code: 42, detail: ["nope"], errors: "nada" });

    expect(failure).toMatchObject({ kind: "unknown", code: "unknown" });
  });

  it("usa o title quando não há detail", () => {
    const failure = toApiFailure(409, { code: "survey.already_published", title: "Já publicada" });

    expect(describeFailure(failure)).toBe("Já publicada");
  });
});

describe("describeFailure", () => {
  it("explica a indisponibilidade da API sem expor detalhe técnico", () => {
    expect(describeFailure(unreachableFailure)).toMatch(/não foi possível falar com a api/i);
  });

  it("exibe o detail do backend sem reescrevê-lo", () => {
    expect(describeFailure(toApiFailure(404, problemBody))).toBe(problemBody.detail);
  });
});

describe("ApiUnavailableError", () => {
  it("carrega a recusa original para o error.tsx do segmento", () => {
    const error = new ApiUnavailableError(unreachableFailure);

    expect(error).toBeInstanceOf(Error);
    expect(error.failure).toEqual(unreachableFailure);
    expect(error.message).toMatch(/não foi possível falar com a api/i);
  });
});
