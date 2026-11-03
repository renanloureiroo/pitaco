package com.renanloureiroo.pitaco.modules.results.application.outputs;

import java.time.Instant;

// Presente só quando a retenção já congelou alguma coisa desta pesquisa. snapshotApplied diz se o
// congelado entrou na conta deste recorte, e a nota explica por quê, para a tela não precisar
// adivinhar.
public record RetentionOutput(boolean snapshotApplied, Instant discardedBefore, String note) {

  public static final String APPLIED_NOTE =
      "Inclui o agregado das respostas descartadas pela política de retenção. As respostas em si "
          + "não existem mais: não aparecem nas respostas abertas nem no export.";

  public static final String NOT_APPLIED_NOTE =
      "As respostas descartadas pela política de retenção não entram em recorte de período ou de "
          + "atributo: o agregado congelado não guarda data nem atributo.";

  public static RetentionOutput applied(Instant discardedBefore) {
    return new RetentionOutput(true, discardedBefore, APPLIED_NOTE);
  }

  public static RetentionOutput notApplied(Instant discardedBefore) {
    return new RetentionOutput(false, discardedBefore, NOT_APPLIED_NOTE);
  }
}
