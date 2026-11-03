import { describe, expect, it } from "vitest";

import { toApiFailure } from "@/shared/api";

import { failureFormState } from "../form-state";

describe("recusa de regra com campo", () => {
  it("o 422 que aponta `field` vira o erro daquele campo", () => {
    const failure = toApiFailure(422, {
      code: "question.condition_value_invalid",
      detail: "Todo valor da condição precisa caber na escala da origem",
      field: "condition.values",
    });

    expect(failure).toMatchObject({ kind: "unknown", field: "condition.values" });

    const state = failureFormState(failure, { statement: "Por quê?" });

    expect(state).toEqual({
      status: "error",
      message: "Todo valor da condição precisa caber na escala da origem",
      fieldErrors: { "condition.values": "Todo valor da condição precisa caber na escala da origem" },
      values: { statement: "Por quê?" },
    });
  });

  it("recusa sem campo continua sem erro por campo", () => {
    const state = failureFormState(toApiFailure(422, { code: "survey.content_frozen", detail: "Congelado." }), {});

    expect(state).toMatchObject({ status: "error", fieldErrors: {} });
  });
});
