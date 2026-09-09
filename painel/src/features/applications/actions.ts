"use server";

import { redirect } from "next/navigation";

import {
  failureFormState,
  invalidFormState,
  readFormValues,
  type FormState,
} from "@/shared/lib";

import { createApplication } from "./api";
import { createApplicationFormSchema } from "./schemas/forms";

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
