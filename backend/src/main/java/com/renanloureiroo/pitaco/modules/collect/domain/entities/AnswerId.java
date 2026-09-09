package com.renanloureiroo.pitaco.modules.collect.domain.entities;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.identity.Id;

public final class AnswerId extends Id {

  private static final String INVALID_CODE = "answer.id_invalid";

  private AnswerId(String value) {
    super(value);
    if (!isUuid(value)) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Identificador de resposta inválido");
    }
  }

  public static AnswerId generate() {
    return new AnswerId(newValue());
  }

  public static AnswerId of(String value) {
    return new AnswerId(value);
  }
}
