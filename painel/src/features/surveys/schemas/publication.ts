import { z } from "zod";

/**
 * Impedimentos de publicação.
 *
 * Lista vazia ⇒ publicação liberada (FR-030). É a **mesma** lista que a publicação usaria para
 * recusar, então a tela nunca diz "pode publicar" e recebe uma recusa de conteúdo em seguida.
 */

export const impedimentCodeSchema = z.enum([
  "survey.no_questions",
  "question.statement_missing",
  "question.options_missing",
  "trigger.missing",
  "trigger.window_invalid",
]);

export type ImpedimentCode = z.infer<typeof impedimentCodeSchema>;
export const IMPEDIMENT_CODES = impedimentCodeSchema.options;

export const publicationImpedimentSchema = z.object({
  code: impedimentCodeSchema,
  field: z.string().optional(),
  /** Quando presente, liga o impedimento à pergunta correspondente na montagem. */
  questionKey: z.string().optional(),
});

export type PublicationImpediment = z.infer<typeof publicationImpedimentSchema>;

export const publicationImpedimentsSchema = z.object({
  impediments: z.array(publicationImpedimentSchema),
});

export const changeKindSchema = z.enum(["cosmetic", "semantic"]);
export type ChangeKind = z.infer<typeof changeKindSchema>;
export const CHANGE_KINDS = changeKindSchema.options;

export const CHANGE_KIND_LABELS: Record<ChangeKind, string> = {
  cosmetic: "Cosmética — respostas continuam comparáveis",
  semantic: "Semântica — abre um novo grupo de comparabilidade",
};

/**
 * `changeKind` é obrigatório **a partir da versão 2** e ignorado na versão 1. Quem decide é a
 * presença de `publishedVersionNumber` na pesquisa, não uma contagem local de versões.
 */
export function publishSurveyFormSchema(hasPublishedVersion: boolean) {
  const changeKind = hasPublishedVersion
    ? z.preprocess(
        (value) => (typeof value === "string" && value !== "" ? value : undefined),
        changeKindSchema,
      )
    : z.preprocess(() => undefined, changeKindSchema.optional());

  return z.object({
    changeKind,
    changeSummary: z.preprocess(
      (value) => (typeof value === "string" && value.trim() !== "" ? value.trim() : undefined),
      z.string().max(500, "O resumo pode ter no máximo 500 caracteres.").optional(),
    ),
  });
}

export type PublishSurveyForm = {
  changeKind?: ChangeKind;
  changeSummary?: string;
};
