package com.renanloureiroo.pitaco.modules.survey.domain.valueobjects;

import com.renanloureiroo.pitaco.core.text.RequiredText;

public record QuestionStatement(String value) {

  private static final int MAX_LENGTH = 500;

  private static final String INVALID_CODE = "question.statement_invalid";

  public QuestionStatement {
    value = RequiredText.of(value, MAX_LENGTH, INVALID_CODE, "Enunciado");
  }

  public static QuestionStatement of(String value) {
    return new QuestionStatement(value);
  }

  @Override
  public String toString() {
    return value;
  }
}
