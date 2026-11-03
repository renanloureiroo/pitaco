package com.renanloureiroo.pitaco.modules.collect.application.errors;

import com.renanloureiroo.pitaco.core.error.NotFoundException;

// Inexistente e de outra aplicação são indistinguíveis por fora (US2.8).
public final class DisplayNotFound extends NotFoundException {

  private static final String CODE = "display.not_found";

  public DisplayNotFound() {
    super(CODE, "Exibição não encontrada");
  }
}
