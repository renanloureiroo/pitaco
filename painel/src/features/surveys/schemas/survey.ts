import { z } from "zod";

import { absent } from "@/shared/api";

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

/** Formato consagrado de onde a pesquisa partiu. Ausente é "em branco". */
export const surveyTemplateSchema = z.enum(["nps", "csat", "ces"]);
export type SurveyTemplate = z.infer<typeof surveyTemplateSchema>;
export const SURVEY_TEMPLATES = surveyTemplateSchema.options;

/**
 * Aviso para não escrever dado pessoal, que o SDK mostra junto dos campos de texto livre. É da
 * pesquisa, não da versão: muda sem abrir rascunho. `text` é o que o respondente vê.
 */
export const freeTextNoticeSchema = z.object({
  enabled: z.boolean(),
  customText: absent(z.string()),
  text: z.string(),
  defaultText: z.string(),
});

export type FreeTextNotice = z.infer<typeof freeTextNoticeSchema>;

export const surveySchema = z.object({
  id: z.string(),
  applicationId: z.string(),
  name: z.string(),
  state: surveyStateSchema,
  publishedVersionNumber: absent(z.number()),
  draftVersionNumber: absent(z.number()),
  priority: z.number().default(0),
  responseQuota: absent(z.number()),
  ignoresQuietPeriod: z.boolean().default(false),
  templateKind: absent(surveyTemplateSchema),
  freeTextNotice: freeTextNoticeSchema.optional(),
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
  trigger: absent(triggerSchema),
});

export type SurveyContent = z.infer<typeof surveyContentSchema>;

export const surveyDetailSchema = z.object({
  id: z.string(),
  applicationId: z.string(),
  name: z.string(),
  state: surveyStateSchema,
  publishedVersionNumber: absent(z.number()),
  draftVersionNumber: absent(z.number()),
  priority: z.number().default(0),
  responseQuota: absent(z.number()),
  ignoresQuietPeriod: z.boolean().default(false),
  templateKind: absent(surveyTemplateSchema),
  freeTextNotice: freeTextNoticeSchema.optional(),
  createdAt: z.string(),
  content: absent(surveyContentSchema),
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

/** Criar: o nome e, opcionalmente, o modelo de onde partir. "Em branco" chega vazio. */
export const surveyCreateFormSchema = surveyNameFormSchema.extend({
  template: z.preprocess(
    (value) => (typeof value === "string" && value !== "" && value !== "blank" ? value : undefined),
    surveyTemplateSchema.optional(),
  ),
});

export type SurveyCreateForm = z.infer<typeof surveyCreateFormSchema>;

/** Duplicar: a aplicação de destino é escolhida; o nome em branco vira "Cópia de" no backend. */
export const duplicateSurveyFormSchema = z.object({
  targetApplicationId: z.preprocess(
    (value) => (typeof value === "string" ? value.trim() : ""),
    z.string().min(1, "Escolha a aplicação que recebe a cópia."),
  ),
  name: z.preprocess(
    (value) => (typeof value === "string" && value.trim() !== "" ? value.trim() : undefined),
    z.string().max(120, "O nome pode ter no máximo 120 caracteres.").optional(),
  ),
});

export type DuplicateSurveyForm = z.infer<typeof duplicateSurveyFormSchema>;

/**
 * A montagem é somente leitura quando a pesquisa está encerrada, ou quando o conteúdo exibido
 * é o publicado sem rascunho aberto — nesse caso, editar exigiria abrir uma nova versão.
 */
export function isAssemblyReadOnly(survey: SurveyDetail): boolean {
  return survey.state === "ended" || survey.content?.source === "published";
}
