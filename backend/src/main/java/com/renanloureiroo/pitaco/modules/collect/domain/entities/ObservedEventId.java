package com.renanloureiroo.pitaco.modules.collect.domain.entities;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.identity.Id;

public final class ObservedEventId extends Id {

  private static final String INVALID_CODE = "observed_event.id_invalid";

  private ObservedEventId(String value) {
    super(value);
    if (!isUuid(value)) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Identificador de evento observado inválido");
    }
  }

  public static ObservedEventId generate() {
    return new ObservedEventId(newValue());
  }

  public static ObservedEventId of(String value) {
    return new ObservedEventId(value);
  }
}
