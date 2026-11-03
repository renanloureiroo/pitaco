import { describe, expect, it } from "vitest";

import { surveyHealthNotices } from "../lib/health-labels";
import { parseSdkErrorFilters } from "../schemas/filters";
import {
  sdkErrorReportSchema,
  sdkVersionsSchema,
  surveyHealthSchema,
  type SurveyHealth,
} from "../schemas/health";

const health: SurveyHealth = {
  from: "2026-08-13T00:00:00Z",
  to: "2026-09-12T00:00:00Z",
  displays: 14,
  suppressions: {
    total: 6,
    byReason: [
      { reason: "unknown_question_type", count: 4 },
      { reason: "unsupported_feature", count: 2 },
    ],
    bySdkVersion: [{ sdkVersion: "0.9.0", count: 6 }],
  },
  suppressionShare: 0.3,
  relevant: true,
  minRequiredVersion: "1.0.0",
  eventName: "checkout.completed",
  eventLastSeenAt: "2026-09-11T10:00:00Z",
};

describe("schemas de saúde", () => {
  it("lê as versões com a proporção ausente quando não houve consulta recente", () => {
    const parsed = sdkVersionsSchema.parse({
      recentFrom: "2026-08-29",
      recentRequests: 0,
      versions: [
        {
          version: "1.0.0",
          requestCount: 10,
          recentRequestCount: 0,
          firstSeenAt: "2026-08-01T00:00:00Z",
          lastSeenAt: "2026-08-01T00:00:00Z",
          stale: true,
        },
      ],
    });

    expect(parsed.versions[0]?.recentShare).toBeUndefined();
    expect(parsed.versions[0]?.stale).toBe(true);
  });

  it("tipo de erro desconhecido vira unknown e contexto ausente vira vazio", () => {
    const parsed = sdkErrorReportSchema.parse({
      id: "e-1",
      kind: "crash",
      message: "x",
      occurredAt: "2026-09-12T10:00:00Z",
      receivedAt: "2026-09-12T10:00:01Z",
    });

    expect(parsed.kind).toBe("unknown");
    expect(parsed.context).toEqual({});
    expect(parsed.sdkVersion).toBeUndefined();
  });

  it("a saúde sem publicação não traz versão mínima nem evento", () => {
    const parsed = surveyHealthSchema.parse({
      ...health,
      minRequiredVersion: undefined,
      eventName: undefined,
      eventLastSeenAt: undefined,
      suppressionShare: null,
    });

    expect(parsed.minRequiredVersion).toBeUndefined();
    expect(parsed.suppressionShare).toBeUndefined();
  });
});

describe("filtros de erros do SDK", () => {
  it("lê tipo e versão da URL", () => {
    expect(parseSdkErrorFilters({ tipo: "render_error", versao: " 1.0.0 " })).toEqual({
      kind: "render_error",
      sdkVersion: "1.0.0",
    });
  });

  it("ignora tipo inválido e versão vazia, e usa o primeiro valor repetido", () => {
    expect(parseSdkErrorFilters({ tipo: "crash", versao: "" })).toEqual({});
    expect(parseSdkErrorFilters({ tipo: ["network_error", "unknown"] })).toEqual({
      kind: "network_error",
    });
  });
});

describe("avisos de saúde da pesquisa", () => {
  it("supressão relevante vira aviso com a proporção, o motivo dominante e a versão mínima", () => {
    const [notice] = surveyHealthNotices(health);

    expect(notice?.kind).toBe("suppression");
    expect(notice?.message).toContain("30%");
    expect(notice?.message).toContain("não conhece um tipo de pergunta");
    expect(notice?.message).toContain("versão 1.0.0");
    expect(notice?.message).toContain("6 supressões");
  });

  it("supressão abaixo do limiar não vira aviso", () => {
    expect(surveyHealthNotices({ ...health, relevant: false })).toEqual([]);
  });

  it("evento configurado que nunca chegou vira aviso próprio", () => {
    const notices = surveyHealthNotices({
      ...health,
      relevant: false,
      eventLastSeenAt: undefined,
    });

    expect(notices).toHaveLength(1);
    expect(notices[0]?.kind).toBe("event_missing");
    expect(notices[0]?.message).toContain('"checkout.completed"');
  });

  it("pesquisa nunca publicada, sem evento, não gera aviso", () => {
    expect(
      surveyHealthNotices({
        ...health,
        relevant: false,
        eventName: undefined,
        eventLastSeenAt: undefined,
      }),
    ).toEqual([]);
  });
});
