import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { API_URL_ENV_VAR } from "@/shared/api";

import { listObservedAttributes } from "../api";

beforeEach(() => {
  vi.stubEnv(API_URL_ENV_VAR, "http://api.test/api");
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe("listObservedAttributes", () => {
  it("lê o catálogo da aplicação com os valores embutidos", async () => {
    const spy = vi.fn(
      async (_input: string) =>
        new Response(
          JSON.stringify({
            items: [
              {
                name: "plano",
                firstSeenAt: "2026-09-10T10:00:00Z",
                lastSeenAt: "2026-09-11T10:00:00Z",
                values: [{ value: "pro", lastSeenAt: "2026-09-11T10:00:00Z" }],
              },
            ],
            page: 0,
            size: 100,
            total: 1,
            totalPages: 1,
          }),
          { status: 200, headers: { "Content-Type": "application/json" } },
        ),
    );
    vi.stubGlobal("fetch", spy);

    const result = await listObservedAttributes("app-1", { page: 0, size: 100 });

    expect(spy.mock.calls[0][0]).toBe(
      "http://api.test/api/applications/app-1/attributes?page=0&size=100",
    );
    expect(result).toMatchObject({
      ok: true,
      data: { items: [{ name: "plano", values: [{ value: "pro" }] }] },
    });
  });
});
