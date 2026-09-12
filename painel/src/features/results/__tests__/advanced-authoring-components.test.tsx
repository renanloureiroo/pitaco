import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { NpsSummaryCard } from "../components/nps-summary-card";
import { QuestionResultCard } from "../components/question-result-card";
import { formatVersionList } from "../lib/results-labels";
import type { QuestionResult } from "../schemas/results";

const escolha: QuestionResult = {
  key: "recomendaria",
  statement: "Recomendaria?",
  type: "single_choice",
  position: 1,
  answered: 2,
  skipped: 0,
  notApplicable: 0,
  aggregate: {
    kind: "choice",
    options: [
      { value: "yes", label: "Sim", count: 1, share: 0.5 },
      { value: "maybe", label: "Talvez", count: 1, share: 0.5 },
    ],
  },
};

describe("QuestionResultCard — versões e não aplicável", () => {
  it("avisa quando a pergunta mudou entre as versões somadas", () => {
    render(<QuestionResultCard question={{ ...escolha, comparability: { comparable: false, versions: [2, 1] } }} />);

    expect(screen.getByTestId("comparability-warning")).toHaveTextContent(
      "Esta pergunta mudou entre as versões 1 e 2",
    );
    expect(screen.queryByTestId("comparability-safe")).not.toBeInTheDocument();
  });

  it("diz que é seguro somar quando nada mudou", () => {
    render(<QuestionResultCard question={{ ...escolha, comparability: { comparable: true, versions: [1, 2] } }} />);

    expect(screen.getByTestId("comparability-safe")).toHaveTextContent("é seguro somar");
  });

  it("uma versão só, ou visão por versão, não traz marca", () => {
    const { rerender } = render(
      <QuestionResultCard question={{ ...escolha, comparability: { comparable: true, versions: [1] } }} />,
    );
    expect(screen.queryByTestId("comparability-safe")).not.toBeInTheDocument();

    rerender(<QuestionResultCard question={escolha} />);
    expect(screen.queryByTestId("comparability-warning")).not.toBeInTheDocument();
  });

  it("conta não aplicáveis à parte das puladas, com a explicação", () => {
    render(<QuestionResultCard question={{ ...escolha, skipped: 1, notApplicable: 3 }} />);

    expect(screen.getByTestId("question-counts")).toHaveTextContent("1 pulada");
    expect(screen.getByTestId("question-counts")).toHaveTextContent("3 não aplicáveis");
    expect(screen.getByTestId("question-not-applicable-note")).toBeInTheDocument();
  });
});

describe("NpsSummaryCard", () => {
  it("mostra o NPS e os três grupos", () => {
    render(
      <NpsSummaryCard
        nps={{ questionKey: "nota", respondents: 3, promoters: 2, passives: 0, detractors: 1, score: 33.3 }}
      />,
    );

    expect(screen.getByTestId("nps-summary-score")).toHaveTextContent("33,3");
    expect(screen.getByTestId("nps-summary")).toHaveTextContent("Calculado sobre 3 respostas");
  });

  it("sem resposta, o número é ausente e não zero", () => {
    render(<NpsSummaryCard nps={{ questionKey: "nota", respondents: 0, promoters: 0, passives: 0, detractors: 0 }} />);

    expect(screen.getByTestId("nps-summary-score")).toHaveTextContent("—");
  });
});

describe("formatVersionList", () => {
  it("lê as versões como numa frase", () => {
    expect(formatVersionList([1])).toBe("1");
    expect(formatVersionList([2, 1])).toBe("1 e 2");
    expect(formatVersionList([3, 1, 2])).toBe("1, 2 e 3");
  });
});
