package com.renanloureiroo.pitaco.modules.app.domain.valueobjects;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;

public record ApiKeyLabel(String value) {

  private static final int MAX_LENGTH = 80;

  private static final String INVALID_CODE = "api_key.label_invalid";

  public ApiKeyLabel {
    if (value == null || value.isBlank()) {
      throw new DomainException(ErrorType.VALIDATION, INVALID_CODE, "Rótulo é obrigatório");
    }
    value = value.strip();
    if (value.length() > MAX_LENGTH) {
      throw new DomainException(
          ErrorType.VALIDATION,
          INVALID_CODE,
          "Rótulo não pode passar de " + MAX_LENGTH + " caracteres");
    }
  }

  public static ApiKeyLabel of(String value) {
    return new ApiKeyLabel(value);
  }

  @Override
  public String toString() {
    return value;
  }
}
