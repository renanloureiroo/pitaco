package com.renanloureiroo.pitaco.modules.collect.application.outputs;

// Situação de saída, não de domínio: EXPIRED nunca é gravado — é decidido na leitura, contra o
// prazo de retenção da aplicação (D-07).
public enum AnswerReadStatus {
  ANSWERED,
  SKIPPED,
  NOT_APPLICABLE,
  EXPIRED
}
