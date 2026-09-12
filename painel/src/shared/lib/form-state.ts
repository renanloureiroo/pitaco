import type { ZodError } from "zod";

import { describeFailure, type ApiFailure } from "@/shared/api";

/**
 * Estado devolvido por toda Server Action ao `useActionState`.
 *
 * Duas garantias da spec moram nesta forma: a recusa vira **mensagem por campo** (FR-004) e o
 * que foi digitado volta intacto em `values`, para que o formulário não se esvazie quando o
 * backend recusa. A forma é idêntica nas três features, por isso é compartilhada.
 */

export type FieldErrors = Record<string, string>;
export type FormValues = Record<string, string>;

export type FormState<TData = undefined> =
  | { status: "idle" }
  | { status: "error"; message: string; fieldErrors: FieldErrors; values: FormValues }
  | { status: "success"; data: TData };

export const idleFormState: FormState<never> = { status: "idle" };

/** Snapshot do que foi digitado, para devolver ao formulário em caso de recusa. */
export function readFormValues(formData: FormData): FormValues {
  const values: FormValues = {};

  for (const [key, value] of formData.entries()) {
    if (typeof value === "string") {
      values[key] = value;
    }
  }

  return values;
}

/** Primeira mensagem por campo — o formulário mostra uma de cada vez. */
export function zodFieldErrors(error: ZodError): FieldErrors {
  const fieldErrors: FieldErrors = {};

  for (const issue of error.issues) {
    const field = issue.path[0];
    if (typeof field === "string" && fieldErrors[field] === undefined) {
      fieldErrors[field] = issue.message;
    }
  }

  return fieldErrors;
}

export function invalidFormState(
  error: ZodError,
  values: FormValues,
  message = "Revise os campos destacados.",
): FormState<never> {
  return { status: "error", message, fieldErrors: zodFieldErrors(error), values };
}

/**
 * Recusa da API como estado do formulário. As mensagens por campo de um `400` viram
 * `fieldErrors`; uma recusa de regra que aponta `field` vira o erro daquele campo. O `detail` do
 * backend é exibido como veio, sem reescrita.
 */
export function failureFormState(failure: ApiFailure, values: FormValues): FormState<never> {
  return {
    status: "error",
    message: describeFailure(failure),
    fieldErrors: fieldErrorsOf(failure),
    values,
  };
}

function fieldErrorsOf(failure: ApiFailure): FieldErrors {
  if (failure.kind === "validation") {
    return failure.errors;
  }
  if (failure.kind !== "unreachable" && failure.field !== undefined) {
    return { [failure.field]: failure.detail };
  }
  return {};
}
