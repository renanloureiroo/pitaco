package com.renanloureiroo.pitaco.modules.collect.domain.entities;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.identity.Id;

// Só `of`: o identificador nasce no dispositivo e é a chave de idempotência do reenvio (D-08).
public final class DisplayId extends Id {

  private static final String INVALID_CODE = "display.id_invalid";

  private DisplayId(String value) {
    super(value);
    if (!isUuid(value)) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Identificador de exibição inválido");
    }
  }

  public static DisplayId of(String value) {
    return new DisplayId(value);
  }
}
