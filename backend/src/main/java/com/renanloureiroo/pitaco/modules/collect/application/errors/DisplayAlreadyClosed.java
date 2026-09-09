package com.renanloureiroo.pitaco.modules.collect.application.errors;

import com.renanloureiroo.pitaco.core.error.ConflictException;

// A primeira gravação vence: uma retentativa atrasada não reescreve dado já coletado (D-08).
public final class DisplayAlreadyClosed extends ConflictException {

  private static final String CODE = "display.already_closed";

  public DisplayAlreadyClosed() {
    super(CODE, "Esta exibição já foi encerrada e não aceita novo desfecho nem resposta nova");
  }
}
