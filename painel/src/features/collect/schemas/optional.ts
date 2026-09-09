import { z } from "zod";

/**
 * Ausência na fronteira da API.
 *
 * A convenção do painel é que campo ausente vira `undefined`, nunca `null` nem valor de
 * preenchimento — é o que permite exibir ausência como ausência. O backend, porém, serializa
 * o campo opcional como `null` em vez de omiti-lo: aceitar só a omissão faria a tela inteira
 * falhar por um `closedAt` de exibição ainda aberta, que é o caso mais comum da listagem.
 *
 * Este envoltório aceita as duas formas na entrada e produz uma só na saída.
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
