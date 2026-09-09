import { z } from "zod";

/**
 * Ausência na fronteira da API.
 *
 * A convenção do painel é que campo ausente vira `undefined`, nunca `null` nem valor de
 * preenchimento — é o que permite exibir ausência como ausência, e distinguir "sem prazo" de
 * "prazo zero".
 *
 * O que o backend faz com o campo ausente, porém, é uma decisão **dele**: hoje ele o omite,
 * mas já o serializou como `null` — e um `.optional()` puro recusa `null`, derrubando a
 * resposta inteira na validação e transformando um campo vazio em tela de erro. Uma fronteira
 * não deve ser tão frágil: este envoltório aceita as duas formas na entrada e produz uma só na
 * saída.
 *
 * Use em todo campo opcional que venha da API. Não use em schema de formulário, que lê
 * `FormData` e nunca vê `null`.
 */
export function absent<T extends z.ZodType>(schema: T) {
  // O `.optional()` do fim não é redundante: sem ele a chave sairia **obrigatória com valor
  // `undefined`** no tipo inferido, e a convenção do painel é que campo ausente seja chave
  // ausente.
  return schema
    .nullish()
    .transform((value) => value ?? undefined)
    .optional();
}
