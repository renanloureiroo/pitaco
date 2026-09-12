package com.renanloureiroo.pitaco.modules.collect.domain.health;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.identity.Id;

public final class SuppressionEventId extends Id {

  private static final String INVALID_CODE = "suppression.id_invalid";

  private SuppressionEventId(String value) {
    super(value);
    if (!isUuid(value)) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Identificador de supressão inválido");
    }
  }

  public static SuppressionEventId generate() {
    return new SuppressionEventId(newValue());
  }

  public static SuppressionEventId of(String value) {
    return new SuppressionEventId(value);
  }
}
