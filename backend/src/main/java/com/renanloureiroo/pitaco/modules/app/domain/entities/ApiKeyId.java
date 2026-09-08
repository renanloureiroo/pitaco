package com.renanloureiroo.pitaco.modules.app.domain.entities;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.identity.Id;

public final class ApiKeyId extends Id {

  private static final String INVALID_CODE = "api_key.id_invalid";

  private ApiKeyId(String value) {
    super(value);
    if (!isUuid(value)) {
      throw new DomainException(
          ErrorType.VALIDATION, INVALID_CODE, "Identificador de chave inválido");
    }
  }

  public static ApiKeyId generate() {
    return new ApiKeyId(newValue());
  }

  public static ApiKeyId of(String value) {
    return new ApiKeyId(value);
  }
}
