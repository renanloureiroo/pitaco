package com.renanloureiroo.pitaco.modules.survey.domain.valueobjects;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;

public record QuestionStatement(String value) {

  private static final int MAX_LENGTH = 500;

  private static final String INVALID_CODE = "question.statement_invalid";

  public QuestionStatement {
    if (value == null || value.isBlank()) {
      throw new DomainException(ErrorType.VALIDATION, INVALID_CODE, "Enunciado é obrigatório");
    }
    value = value.strip();
    if (value.length() > MAX_LENGTH) {
      throw new DomainException(
          ErrorType.VALIDATION,
          INVALID_CODE,
          "Enunciado não pode passar de " + MAX_LENGTH + " caracteres");
    }
  }

  public static QuestionStatement of(String value) {
    return new QuestionStatement(value);
  }

  @Override
  public String toString() {
    return value;
  }
}
