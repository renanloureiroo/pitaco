package com.renanloureiroo.pitaco.modules.app.domain.valueobjects;

import com.renanloureiroo.pitaco.core.text.RequiredText;

public record ApiKeyLabel(String value) {

  private static final int MAX_LENGTH = 80;

  private static final String INVALID_CODE = "api_key.label_invalid";

  public ApiKeyLabel {
    value = RequiredText.of(value, MAX_LENGTH, INVALID_CODE, "Rótulo");
  }

  public static ApiKeyLabel of(String value) {
    return new ApiKeyLabel(value);
  }

  @Override
  public String toString() {
    return value;
  }
}
