import { describe, expect, it } from "vitest";

import type { ResponseRate } from "@/features/results";

import {
  mergeTimelines,
  parseOverviewPeriod,
  periodWindows,
  pointChange,
  relativeChange,
  sumNps,
  sumTotals,
} from "../lib/aggregate";

function rate(displayed: number, completed: number, extra: Partial<ResponseRate> = {}): ResponseRate {
  return {
    displayed,
    completed,
    dismissed: 0,
    abandoned: 0,
    inProgress: 0,
    definition: "",
    timeline: [],
    ...extra,
  };
}

describe("sumTotals", () => {
  it("a taxa da aplicação vem das contagens somadas, não da média das taxas", () => {
    const totals = sumTotals([rate(100, 50), rate(10, 10)]);
    expect(totals.displayed).toBe(110);
    expect(totals.completed).toBe(60);
    expect(totals.rate).toBeCloseTo(60 / 110);
  });

  it("sem exibição não há taxa — ausente, nunca 0%", () => {
    expect(sumTotals([]).rate).toBeUndefined();
    expect(sumTotals([rate(0, 0)]).rate).toBeUndefined();
  });
});

describe("sumNps", () => {
  it("recalcula o NPS sobre os grupos somados", () => {
    const nps = sumNps([
      { questionKey: "a", respondents: 10, promoters: 6, passives: 2, detractors: 2, score: 40 },
      { questionKey: "b", respondents: 10, promoters: 2, passives: 2, detractors: 6, score: -40 },
    ]);
    expect(nps?.respondents).toBe(20);
    expect(nps?.score).toBe(0);
  });

  it("sem pesquisa de NPS é ausente; sem resposta não tem pontuação", () => {
    expect(sumNps([undefined])).toBeUndefined();
    expect(
      sumNps([{ questionKey: "a", respondents: 0, promoters: 0, passives: 0, detractors: 0 }])?.score,
    ).toBeUndefined();
  });
});

describe("mergeTimelines", () => {
  it("soma por dia e preenche com zero os dias sem exibição do período", () => {
    const merged = mergeTimelines(
      [
        [{ day: "2026-09-01", displayed: 3, completed: 1 }],
        [
          { day: "2026-09-01", displayed: 2, completed: 2 },
          { day: "2026-09-03", displayed: 1, completed: 0 },
        ],
      ],
      new Date("2026-09-01T10:00:00Z"),
      new Date("2026-09-03T08:00:00Z"),
    );
    expect(merged).toEqual([
      { day: "2026-09-01", displayed: 5, completed: 3 },
      { day: "2026-09-02", displayed: 0, completed: 0 },
      { day: "2026-09-03", displayed: 1, completed: 0 },
    ]);
  });
});

describe("variações", () => {
  it("relativa precisa de base no período anterior", () => {
    expect(relativeChange(150, 100)).toBeCloseTo(0.5);
    expect(relativeChange(10, 0)).toBeUndefined();
  });

  it("em pontos precisa dos dois lados", () => {
    expect(pointChange(0.5, 0.4)).toBeCloseTo(0.1);
    expect(pointChange(undefined, 0.4)).toBeUndefined();
  });
});

describe("período", () => {
  it("valor fora da lista cai no padrão de 30 dias", () => {
    expect(parseOverviewPeriod("7")).toBe("7");
    expect(parseOverviewPeriod("365")).toBe("30");
    expect(parseOverviewPeriod(undefined)).toBe("30");
  });

  it("o período anterior tem a mesma duração e termina onde o atual começa", () => {
    const now = new Date("2026-10-01T00:00:00Z");
    const windows = periodWindows("7", now);
    expect(windows.current.from).toBe("2026-09-24T00:00:00.000Z");
    expect(windows.previous).toEqual({ from: "2026-09-17T00:00:00.000Z", to: "2026-09-24T00:00:00.000Z" });
  });
});
