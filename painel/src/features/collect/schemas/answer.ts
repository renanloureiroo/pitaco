import { z } from "zod";

import { absent } from "./optional";

/**
 * Resposta e sua situação.
 *
 * As três situações **nunca** podem colapsar em "resposta vazia" (FR-012, SC-006): `EXPIRED`
 * fala de dado que existiu e foi descartado pela retenção de texto livre da aplicação;
 * `SKIPPED` fala de escolha de quem respondeu. São coisas diferentes para quem lê.
 */

export const answerStatusSchema = z.enum(["ANSWERED", "SKIPPED", "EXPIRED"]);
export type AnswerStatus = z.infer<typeof answerStatusSchema>;
export const ANSWER_STATUSES = answerStatusSchema.options;

export const answerSchema = z.object({
  /** Chave estável da pergunta: é por ela que a resposta encontra o enunciado da versão. */
  questionKey: z.string(),
  status: answerStatusSchema,
  /** Só em pergunta de texto livre. */
  text: absent(z.string()),
  /** Só em pergunta numérica. `0` é valor, não ausência. */
  number: absent(z.number()),
  /** Sempre presente; vazio fora das perguntas de escolha. */
  options: z.array(z.string()),
});

export type Answer = z.infer<typeof answerSchema>;
