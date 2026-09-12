import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { RetentionNote } from "../components/retention-note";
import { surveyResultsSchema } from "../schemas/results";

describe("RetentionNote", () => {
  it("quando o agregado guardado entrou na conta, diz que os números o incluem", () => {
    render(
      <RetentionNote
        retention={{ snapshotApplied: true, discardedBefore: "2026-08-01T12:00:00Z", note: "" }}
      />,
    );

    expect(screen.getByTestId("retention-note")).toHaveTextContent("Inclui agregados");
    expect(screen.getByTestId("retention-note")).toHaveTextContent("01/08/2026");
  });

  it("quando o recorte impede somar, diz que as respostas anteriores ficaram de fora", () => {
    render(
      <RetentionNote
        retention={{ snapshotApplied: false, discardedBefore: "2026-08-01T12:00:00Z", note: "" }}
      />,
    );

    expect(screen.getByTestId("retention-note")).toHaveTextContent("não entram neste recorte");
  });

  it("o resultado sem retenção não traz o campo", () => {
    const parsed = surveyResultsSchema.parse({
      everPublished: true,
      responseRate: {
        displayed: 0,
        completed: 0,
        dismissed: 0,
        abandoned: 0,
        inProgress: 0,
        definition: "x",
        timeline: [],
      },
      questions: [],
      sampleSize: 0,
      smallSample: true,
      filter: { attributeAbsent: false },
      attributes: [],
    });

    expect(parsed.retention).toBeUndefined();
  });
});
