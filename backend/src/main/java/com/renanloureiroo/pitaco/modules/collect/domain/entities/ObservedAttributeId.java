package com.renanloureiroo.pitaco.modules.collect.domain.entities;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.identity.Id;

public final class ObservedAttributeId extends Id {

  private static final String INVALID_CODE = "observed_attribute.id_invalid";

  private ObservedAttributeId(String value) {
    super(value);
    if (!isUuid(value)) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Identificador de atributo observado inválido");
    }
  }

  public static ObservedAttributeId generate() {
    return new ObservedAttributeId(newValue());
  }

  public static ObservedAttributeId of(String value) {
    return new ObservedAttributeId(value);
  }
}
