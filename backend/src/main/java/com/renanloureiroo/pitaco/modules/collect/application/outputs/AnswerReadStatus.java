package com.renanloureiroo.pitaco.modules.collect.application.outputs;

// Situação de saída, não de domínio: AnswerStatus continua com dois valores e EXPIRED nunca é
// gravado — é decidido na leitura, contra o prazo de retenção da aplicação (D-07).
public enum AnswerReadStatus {
  ANSWERED,
  SKIPPED,
  EXPIRED
}
