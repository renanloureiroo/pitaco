import userEvent from "@testing-library/user-event";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { OpenAnswer, QuestionResult, ResponseRate } from "../schemas/results";

const navigation = vi.hoisted(() => ({
  push: vi.fn(),
  refresh: vi.fn(),
  pathname: "/aplicacoes/app-1/pesquisas/srv-1/resultados",
  searchParams: new URLSearchParams(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: navigation.push, refresh: navigation.refresh }),
  usePathname: () => navigation.pathname,
  useSearchParams: () => navigation.searchParams,
}));

const { ResponseRateCard } = await import("../components/response-rate-card");
const { QuestionResultCard } = await import("../components/question-result-card");
const { OpenAnswersList } = await import("../components/open-answers-list");
const { OpenAnswersSearch } = await import("../components/open-answers-search");
const { ResultsFiltersForm } = await import("../components/results-filters");
const { ExportButton } = await import("../components/export-button");
const { resultsEmptyVariant } = await import("../lib/results-labels");

const responseRate: ResponseRate = {
  displayed: 5,
  completed: 2,
  dismissed: 1,
  abandoned: 1,
  inProgress: 1,
  rate: 0.4,
  definition: "Taxa de resposta = concluídas ÷ exibidas.",
  timeline: [{ day: "2026-09-01", displayed: 5, completed: 2 }],
};

beforeEach(() => {
  navigation.push.mockReset();
  navigation.refresh.mockReset();
  navigation.searchParams = new URLSearchParams();
});

describe("taxa de resposta", () => {
  it("mostra as quatro contagens, a taxa e a definição do cálculo junto dos números", () => {
    render(<ResponseRateCard responseRate={responseRate} smallSample />);

    expect(screen.getByTestId("response-rate-value")).toHaveTextContent("40%");
    expect(screen.getByTestId("response-rate-definition")).toHaveTextContent("concluídas ÷ exibidas");
    expect(screen.getByTestId("stat-displayed")).toHaveTextContent("5");
    expect(screen.getByTestId("stat-abandoned")).toHaveTextContent("1");
    expect(screen.getByTestId("small-sample-badge")).toBeVisible();
    expect(screen.getAllByTestId("timeline-point")).toHaveLength(1);
  });

  it("sem exibição não há taxa nem aviso de amostra", () => {
    render(
      <ResponseRateCard
        responseRate={{ ...responseRate, displayed: 0, completed: 0, rate: undefined, timeline: [] }}
        smallSample
      />,
    );

    expect(screen.getByTestId("response-rate-value")).toHaveTextContent("—");
    expect(screen.queryByTestId("small-sample-badge")).not.toBeInTheDocument();
  });
});

describe("agregado por pergunta", () => {
  const base: QuestionResult = {
    key: "q1",
    statement: "Recomendaria?",
    type: "single_choice",
    position: 1,
    answered: 4,
    skipped: 1,
    notApplicable: 0,
    aggregate: {
      kind: "choice",
      options: [
        { value: "yes", label: "Sim", count: 3, share: 0.75 },
        { value: "no", label: "Não", count: 0, share: 0 },
      ],
    },
  };

  it("escolha traz contagem absoluta e proporção por opção, inclusive zero", () => {
    render(<QuestionResultCard question={base} />);

    const rows = screen.getAllByTestId("aggregate-row");
    expect(rows).toHaveLength(2);
    expect(rows[0]).toHaveTextContent("Sim");
    expect(rows[0]).toHaveTextContent("3 · 75%");
    expect(rows[1]).toHaveTextContent("0 · 0%");
    expect(screen.getByTestId("question-counts")).toHaveTextContent("4 respondidas · 1 pulada");
  });

  it("pergunta sem resposta é apresentada como tal, não como zero", () => {
    render(<QuestionResultCard question={{ ...base, answered: 0, aggregate: undefined }} />);

    expect(screen.getByTestId("question-no-answers")).toBeVisible();
    expect(screen.queryAllByTestId("aggregate-row")).toHaveLength(0);
  });

  it("NPS mostra os três grupos e a pontuação", () => {
    render(
      <QuestionResultCard
        question={{
          ...base,
          type: "nps",
          aggregate: {
            kind: "nps",
            promoters: 6,
            passives: 2,
            detractors: 2,
            score: 40,
            distribution: [{ value: 10, count: 6, share: 0.6 }],
          },
        }}
      />,
    );

    expect(screen.getByTestId("nps-score")).toHaveTextContent("40");
    expect(screen.getByTestId("nps-promoters")).toHaveTextContent("6");
    expect(screen.getByTestId("nps-detractors")).toHaveTextContent("2");
  });

  it("avaliação mostra a média", () => {
    render(
      <QuestionResultCard
        question={{
          ...base,
          type: "rating",
          aggregate: { kind: "numeric", average: 4.25, distribution: [{ value: 4, count: 4, share: 1 }] },
        }}
      />,
    );

    expect(screen.getByTestId("question-average")).toHaveTextContent("4,3");
  });
});

describe("respostas abertas", () => {
  const answer: OpenAnswer = {
    displayId: "dsp-1",
    questionKey: "q2",
    statement: "O que achou?",
    text: "Achei confuso",
    answeredAt: "2026-09-08T14:22:31Z",
    context: [{ questionKey: "q1", statement: "De 0 a 10?", value: "9" }],
  };

  it("cada resposta traz o momento e o contexto da mesma exibição", () => {
    render(<OpenAnswersList answers={[answer]} />);

    expect(screen.getByTestId("open-answer-text")).toHaveTextContent("Achei confuso");
    expect(screen.getByTestId("open-answer")).toHaveTextContent("08/09/2026");
    expect(screen.getByTestId("context-value")).toHaveTextContent("9");
  });

  it("a busca vai para a URL e zera a página", async () => {
    navigation.searchParams = new URLSearchParams("page=2&periodo=7");
    render(<OpenAnswersSearch />);

    await userEvent.type(screen.getByTestId("search-input"), "confuso");
    await userEvent.click(screen.getByTestId("search-button"));

    expect(navigation.push).toHaveBeenCalledWith(
      "/aplicacoes/app-1/pesquisas/srv-1/resultados?periodo=7&q=confuso",
    );
  });
});

describe("recorte", () => {
  it("aplica atalho de período e atributo ausente na URL, e limpar remove tudo", async () => {
    navigation.searchParams = new URLSearchParams("q=x&page=3");
    render(
      <ResultsFiltersForm
        filters={{}}
        attributes={[{ name: "plano", values: [{ value: "pro", count: 2 }] }]}
        versions={[2, 1]}
      />,
    );

    await userEvent.click(screen.getByTestId("filter-period"));
    await userEvent.click(screen.getByRole("option", { name: "Últimos 7 dias" }));
    await userEvent.click(screen.getByTestId("filter-attribute"));
    await userEvent.click(screen.getByRole("option", { name: "plano" }));
    await userEvent.click(screen.getByTestId("filter-value"));
    await userEvent.click(screen.getByRole("option", { name: "Sem o atributo" }));
    await userEvent.click(screen.getByTestId("apply-filters"));

    expect(navigation.push).toHaveBeenLastCalledWith(
      "/aplicacoes/app-1/pesquisas/srv-1/resultados?q=x&periodo=7&atributo=plano&ausente=1",
    );

    await userEvent.click(screen.getByTestId("clear-filters"));
    expect(navigation.push).toHaveBeenLastCalledWith("/aplicacoes/app-1/pesquisas/srv-1/resultados?q=x");
  });

  it("período personalizado invertido é recusado sem navegar", async () => {
    render(<ResultsFiltersForm filters={{ from: "2026-09-10T10:00", to: "2026-09-08T10:00" }} attributes={[]} versions={[1]} />);

    await userEvent.click(screen.getByTestId("apply-filters"));

    expect(screen.getByTestId("field-error-periodo")).toBeVisible();
    expect(navigation.push).not.toHaveBeenCalled();
  });
});

describe("export e vazios", () => {
  it("o export só navega depois da confirmação com o lembrete de dado pessoal", async () => {
    const assign = vi.fn();
    vi.stubGlobal("location", { ...window.location, assign });
    render(<ExportButton href="/api/aplicacoes/app-1/pesquisas/srv-1/resultados/export" />);

    await userEvent.click(screen.getByTestId("export-button"));
    expect(screen.getByTestId("confirm-dialog")).toHaveTextContent("dado pessoal");
    expect(assign).not.toHaveBeenCalled();

    await userEvent.click(screen.getByTestId("confirm-button"));
    expect(assign).toHaveBeenCalledWith("/api/aplicacoes/app-1/pesquisas/srv-1/resultados/export");
    vi.unstubAllGlobals();
  });

  it("distingue nunca publicada, sem exibição e sem exibição no recorte", () => {
    expect(resultsEmptyVariant({ everPublished: false, displayed: 0, filtered: true })).toBe("never_published");
    expect(resultsEmptyVariant({ everPublished: true, displayed: 0, filtered: false })).toBe("no_displays");
    expect(resultsEmptyVariant({ everPublished: true, displayed: 0, filtered: true })).toBe("no_matches");
    expect(resultsEmptyVariant({ everPublished: true, displayed: 3, filtered: true })).toBeUndefined();
  });
});
