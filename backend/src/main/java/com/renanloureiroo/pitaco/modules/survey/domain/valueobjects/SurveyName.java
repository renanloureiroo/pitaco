package com.renanloureiroo.pitaco.modules.survey.domain.valueobjects;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;

public record SurveyName(String value) {

  private static final int MAX_LENGTH = 120;

  private static final String INVALID_CODE = "survey.name_invalid";

  public SurveyName {
    if (value == null || value.isBlank()) {
      throw new DomainException(ErrorType.VALIDATION, INVALID_CODE, "Nome é obrigatório");
    }
    value = value.strip();
    if (value.length() > MAX_LENGTH) {
      throw new DomainException(
          ErrorType.VALIDATION,
          INVALID_CODE,
          "Nome não pode passar de " + MAX_LENGTH + " caracteres");
    }
  }

  public static SurveyName of(String value) {
    return new SurveyName(value);
  }

  @Override
  public String toString() {
    return value;
  }
}
