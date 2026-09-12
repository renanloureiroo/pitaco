package com.renanloureiroo.pitaco.modules.collect.domain.entities;

// Pulada e não aplicável parecem a mesma coisa e não são: pulada é quem viu a pergunta e não
// respondeu, sinal sobre a pergunta; não aplicável é quem nunca a viu, porque a condição a tirou
// do caminho.
public enum AnswerStatus {
  ANSWERED,
  SKIPPED,
  NOT_APPLICABLE
}
