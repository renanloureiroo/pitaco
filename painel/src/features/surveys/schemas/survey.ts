import { z } from "zod";

import { questionSchema } from "./question";
import { triggerSchema } from "./trigger";

/**
 * Pesquisa: listagem, detalhe e estado.
 *
 * `publishedVersionNumber` **ausente** significa nunca publicada — é o que libera a ação de
 * descartar (FR-020). Ausente, não `0`: um número de versão zero não existe no domínio.
 */

export const surveyStateSchema = z.enum(["draft", "scheduled", "active", "paused", "ended"]);
export type SurveyState = z.infer<typeof surveyStateSchema>;
export const SURVEY_STATES = surveyStateSchema.options;

export const surveySchema = z.object({
  id: z.string(),
  applicationId: z.string(),
  name: z.string(),
  state: surveyStateSchema,
  publishedVersionNumber: z.number().optional(),
  draftVersionNumber: z.number().optional(),
  createdAt: z.string(),
});

export type Survey = z.infer<typeof surveySchema>;

/**
 * `content` é ausente enquanto não existe versão nenhuma. `content.source` é o que a tela de
 * montagem usa para decidir entre editar o rascunho e exibir o publicado em leitura — a
 * decisão vem do backend, não de dedução sobre o estado.
 */
export const surveyContentSchema = z.object({
  source: z.enum(["draft", "published"]),
  versionNumber: z.number(),
  questions: z.array(questionSchema),
  trigger: triggerSchema.optional(),
});

export type SurveyContent = z.infer<typeof surveyContentSchema>;

export const surveyDetailSchema = z.object({
  id: z.string(),
  applicationId: z.string(),
  name: z.string(),
  state: surveyStateSchema,
  publishedVersionNumber: z.number().optional(),
  draftVersionNumber: z.number().optional(),
  createdAt: z.string(),
  content: surveyContentSchema.optional(),
});

export type SurveyDetail = z.infer<typeof surveyDetailSchema>;

/** Formulário de criação e de renome: só o nome. */
export const surveyNameFormSchema = z.object({
  name: z.preprocess(
    (value) => (typeof value === "string" ? value.trim() : ""),
    z
      .string()
      .min(1, "Informe o nome da pesquisa.")
      .max(160, "O nome pode ter no máximo 160 caracteres."),
  ),
});

export type SurveyNameForm = z.infer<typeof surveyNameFormSchema>;

/**
 * A montagem é somente leitura quando a pesquisa está encerrada, ou quando o conteúdo exibido
 * é o publicado sem rascunho aberto — nesse caso, editar exigiria abrir uma nova versão.
 */
export function isAssemblyReadOnly(survey: SurveyDetail): boolean {
  return survey.state === "ended" || survey.content?.source === "published";
}
