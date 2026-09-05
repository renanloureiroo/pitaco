package com.renanloureiroo.pitaco.modules.app.domain.entities;

import com.renanloureiroo.pitaco.core.identity.Id;

/** Identificador de {@link Application}. */
public final class ApplicationId extends Id {

  private ApplicationId(String value) {
    super(value);
  }

  /** Identificador de uma aplicação que ainda não existia. */
  public static ApplicationId generate() {
    return new ApplicationId(newValue());
  }

  /** Identificador já existente, vindo da borda ou da persistência. */
  public static ApplicationId of(String value) {
    return new ApplicationId(value);
  }
}
