package com.renanloureiroo.pitaco.modules.collect.application.errors;

import com.renanloureiroo.pitaco.core.error.ConflictException;

public final class DisplayIdentifierConflict extends ConflictException {

  private static final String CODE = "display.identifier_conflict";

  public DisplayIdentifierConflict() {
    super(CODE, "Este identificador de exibição já foi usado para outra pesquisa ou versão");
  }
}
