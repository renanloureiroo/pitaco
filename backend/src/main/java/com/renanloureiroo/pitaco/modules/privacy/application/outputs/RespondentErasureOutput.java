package com.renanloureiroo.pitaco.modules.privacy.application.outputs;

// Pedido repetido não é erro: não há o que apagar, e a resposta diz só isso.
public record RespondentErasureOutput(boolean deleted, int displaysDeleted, int answersDeleted) {

  public static RespondentErasureOutput nothingToDelete() {
    return new RespondentErasureOutput(false, 0, 0);
  }
}
