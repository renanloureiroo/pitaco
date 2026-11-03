import type { PublicationImpediment } from "../schemas/publication";

/**
 * Cada `code` de impedimento vira uma frase **acionável**: quem lê precisa saber o que fazer,
 * não apenas que algo está errado.
 */
const MESSAGES: Record<PublicationImpediment["code"], string> = {
  "survey.no_questions": "Adicione ao menos uma pergunta antes de publicar.",
  "question.statement_missing": "Uma pergunta está sem enunciado. Escreva o enunciado dela.",
  "question.options_missing":
    "Uma pergunta de escolha está sem opções. Adicione as opções de resposta.",
  "trigger.missing": "Defina o disparo: sem ele, a pesquisa não aparece para ninguém.",
  "trigger.window_invalid":
    "A janela do disparo é inválida. O fim precisa ser posterior ao início.",
};

export function impedimentMessage(impediment: PublicationImpediment): string {
  const message = MESSAGES[impediment.code];

  return impediment.field === undefined ? message : `${message} (campo: ${impediment.field})`;
}
