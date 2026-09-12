"use server";

import { refresh } from "next/cache";

import {
  failureFormState,
  invalidFormState,
  readFormValues,
  type FormState,
} from "@/shared/lib";

import { eraseRespondent } from "./api/privacy";
import { eraseRespondentFormSchema } from "./schemas/forms";
import type { RespondentErasure } from "./schemas/privacy";

/**
 * Termina na mesma tela: `refresh()` traz o registro novo para a lista de exclusões. O resultado
 * volta no estado para a tela dizer quanto saiu — ou que não havia o que apagar.
 */
export async function eraseRespondentAction(
  applicationId: string,
  _previous: FormState<RespondentErasure>,
  formData: FormData,
): Promise<FormState<RespondentErasure>> {
  const values = readFormValues(formData);
  const parsed = eraseRespondentFormSchema.safeParse(values);

  if (!parsed.success) {
    return invalidFormState(parsed.error, values);
  }

  const result = await eraseRespondent(applicationId, parsed.data);

  if (!result.ok) {
    return failureFormState(result, values);
  }

  refresh();
  return { status: "success", data: result.data };
}
