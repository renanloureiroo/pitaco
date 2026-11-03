package com.renanloureiroo.pitaco.modules.collect.domain.entities;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.identity.Id;

public final class RespondentId extends Id {

  private static final String INVALID_CODE = "respondent.id_invalid";

  private RespondentId(String value) {
    super(value);
    if (!isUuid(value)) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Identificador de respondente inválido");
    }
  }

  public static RespondentId generate() {
    return new RespondentId(newValue());
  }

  public static RespondentId of(String value) {
    return new RespondentId(value);
  }
}
