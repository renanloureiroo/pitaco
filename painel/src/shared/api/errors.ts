import { z } from "zod";

/**
 * Fronteira de erro do painel (R5).
 *
 * O cliente HTTP **nunca lança** para erro esperado: devolve um resultado discriminado. Quem
 * lê decide o tratamento — em leitura, `not_found` vira `notFound()` e `unknown`/`unreachable`
 * são lançados para o `error.tsx` do segmento; em ação, a recusa vira estado do `useActionState`.
 *
 * O corpo de recusa do backend é RFC 9457 (`application/problem+json`), com `code` estável e
 * `traceId`. O `detail` é exibido como veio: o painel nunca reescreve a mensagem do backend.
 */

export type ApiFailureKind =
  | "validation"
  | "not_found"
  | "conflict"
  | "forbidden"
  | "unknown"
  | "unreachable";

export type ApiFailure =
  | {
      ok: false;
      kind: "validation";
      code: string;
      detail: string;
      traceId?: string;
      /** mensagem por campo, vinda de `errors` do corpo `ApiValidationError` */
      errors: Record<string, string>;
    }
  | {
      ok: false;
      kind: "not_found" | "conflict" | "forbidden" | "unknown";
      code: string;
      detail: string;
      traceId?: string;
      /** campo que causou a recusa de regra, quando o backend o aponta (ex.: `condition.values`) */
      field?: string;
    }
  | { ok: false; kind: "unreachable" };

export type Result<T> = { ok: true; data: T } | ApiFailure;

/** Corpo RFC 9457 do backend. Tudo é opcional: uma recusa pode chegar sem corpo ou malformada. */
const problemDetailsSchema = z.object({
  type: z.string().optional(),
  title: z.string().optional(),
  status: z.number().optional(),
  detail: z.string().optional(),
  instance: z.string().optional(),
  code: z.string().optional(),
  traceId: z.string().optional(),
  errors: z.record(z.string(), z.string()).optional(),
  field: z.string().optional(),
});

export type ProblemDetails = z.infer<typeof problemDetailsSchema>;

const UNKNOWN_CODE = "unknown";
const FALLBACK_DETAIL = "A API recusou a requisição sem detalhar o motivo.";

export function statusToKind(status: number): Exclude<ApiFailureKind, "unreachable"> {
  switch (status) {
    case 400:
      return "validation";
    case 403:
      return "forbidden";
    case 404:
      return "not_found";
    case 409:
      return "conflict";
    default:
      return "unknown";
  }
}

/** Traduz `status` + corpo (possivelmente ausente ou malformado) em recusa tipada. */
export function toApiFailure(status: number, body: unknown): ApiFailure {
  const parsed = problemDetailsSchema.safeParse(body);
  const problem: ProblemDetails = parsed.success ? parsed.data : {};

  const kind = statusToKind(status);
  const code = problem.code ?? UNKNOWN_CODE;
  const detail = problem.detail ?? problem.title ?? FALLBACK_DETAIL;

  if (kind === "validation") {
    return {
      ok: false,
      kind,
      code,
      detail,
      ...(problem.traceId !== undefined ? { traceId: problem.traceId } : {}),
      errors: problem.errors ?? {},
    };
  }

  return {
    ok: false,
    kind,
    code,
    detail,
    ...(problem.traceId !== undefined ? { traceId: problem.traceId } : {}),
    ...(problem.field !== undefined ? { field: problem.field } : {}),
  };
}

export const unreachableFailure: ApiFailure = { ok: false, kind: "unreachable" };

/** Recusa que a tela não sabe tratar e que deve subir para o `error.tsx` do segmento. */
export class ApiUnavailableError extends Error {
  readonly failure: ApiFailure;

  constructor(failure: ApiFailure) {
    super(describeFailure(failure));
    this.name = "ApiUnavailableError";
    this.failure = failure;
  }
}

/** Texto exibível de uma recusa. Preserva o `detail` do backend quando existe. */
export function describeFailure(failure: ApiFailure): string {
  if (failure.kind === "unreachable") {
    return "Não foi possível falar com a API do Pitaco. Verifique se ela está no ar e tente novamente.";
  }

  return failure.detail;
}
