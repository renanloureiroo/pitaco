"use server";

import { refresh } from "next/cache";
import { redirect } from "next/navigation";

import {
  failureFormState,
  invalidFormState,
  readFormValues,
  type FormState,
} from "@/shared/lib";

import { activateApplication, createApplication, deactivateApplication, updateApplication } from "./api";
import { createApplicationFormSchema, updateApplicationFormSchema } from "./schemas/forms";

/**
 * Mutações de aplicação.
 *
 * A action nunca lança para recusa esperada: devolve o estado que o `useActionState` renderiza
 * no formulário, com o que foi digitado intacto (FR-004). Em sucesso, o fluxo termina em outra
 * tela — por isso `redirect` para o detalhe, e não revalidação.
 */
export async function createApplicationAction(
  _previous: FormState,
  formData: FormData,
): Promise<FormState> {
  const values = readFormValues(formData);
  const parsed = createApplicationFormSchema.safeParse(values);

  if (!parsed.success) {
    return invalidFormState(parsed.error, values);
  }

  const result = await createApplication(parsed.data);

  if (!result.ok) {
    return failureFormState(result, values);
  }

  // `redirect` lança um controle de fluxo tratado pelo framework: nada abaixo executa.
  redirect(`/aplicacoes/${result.data.id}`);
}

/** Edição termina na mesma tela: `refresh()` realinha o detalhe com o que o backend gravou. */
export async function updateApplicationAction(
  applicationId: string,
  _previous: FormState,
  formData: FormData,
): Promise<FormState> {
  const values = readFormValues(formData);
  const parsed = updateApplicationFormSchema.safeParse(values);

  if (!parsed.success) {
    return invalidFormState(parsed.error, values);
  }

  const result = await updateApplication(applicationId, parsed.data);

  if (!result.ok) {
    return failureFormState(result, values);
  }

  refresh();
  return { status: "success", data: undefined };
}

export async function setApplicationStatusAction(
  applicationId: string,
  action: "activate" | "deactivate",
): Promise<FormState> {
  const result =
    action === "deactivate"
      ? await deactivateApplication(applicationId)
      : await activateApplication(applicationId);

  if (!result.ok) {
    return failureFormState(result, {});
  }

  refresh();
  return { status: "success", data: undefined };
}
