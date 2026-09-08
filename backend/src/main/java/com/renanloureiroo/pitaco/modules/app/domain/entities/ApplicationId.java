package com.renanloureiroo.pitaco.modules.app.domain.entities;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.identity.Id;

public final class ApplicationId extends Id {

  private static final String INVALID_CODE = "application.id_invalid";

  private ApplicationId(String value) {
    super(value);
    if (!isUuid(value)) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Identificador de aplicação inválido");
    }
  }

  public static ApplicationId generate() {
    return new ApplicationId(newValue());
  }

  public static ApplicationId of(String value) {
    return new ApplicationId(value);
  }
}
