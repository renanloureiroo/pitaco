"use server";

import { refresh } from "next/cache";

import {
  failureFormState,
  invalidFormState,
  readFormValues,
  type FormState,
} from "@/shared/lib";

import { issueApiKey, revokeApiKey } from "./api";
import { issueApiKeyFormSchema, type IssuedApiKey } from "./schemas/api-key";

/**
 * O segredo emitido volta **apenas** no estado desta action (R13). Daqui ele vai para o
 * `useState` do diálogo e é descartado ao fechar. Nunca entra em URL, `searchParams`,
 * `localStorage`, cookie ou log — e nenhuma leitura o traz de volta.
 */
export async function issueApiKeyAction(
  applicationId: string,
  _previous: FormState<IssuedApiKey | undefined>,
  formData: FormData,
): Promise<FormState<IssuedApiKey | undefined>> {
  const values = readFormValues(formData);
  const parsed = issueApiKeyFormSchema.safeParse(values);

  if (!parsed.success) {
    return invalidFormState(parsed.error, values);
  }

  const result = await issueApiKey(applicationId, parsed.data.label);

  if (!result.ok) {
    return failureFormState(result, values);
  }

  refresh();
  return { status: "success", data: result.data };
}

export async function revokeApiKeyAction(
  applicationId: string,
  apiKeyId: string,
): Promise<FormState> {
  const result = await revokeApiKey(applicationId, apiKeyId);

  // O 409 de chave já revogada é recusa exibível: alguém pode tê-la revogado em paralelo.
  if (!result.ok) {
    return failureFormState(result, {});
  }

  refresh();
  return { status: "success", data: undefined };
}
