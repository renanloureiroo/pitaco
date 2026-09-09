package com.renanloureiroo.pitaco.core.catalog;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.text.RequiredText;

public record QuestionOption(String label, String value, int position) {

  private static final int MAX_LABEL_LENGTH = 200;
  private static final int MAX_VALUE_LENGTH = 120;

  private static final String INVALID_CODE = "question.option_invalid";

  public QuestionOption {
    label = RequiredText.of(label, MAX_LABEL_LENGTH, INVALID_CODE, "Rótulo da opção");
    value = RequiredText.of(value, MAX_VALUE_LENGTH, INVALID_CODE, "Valor da opção");
    if (position < 1) {
      throw new DomainException(ErrorType.VALIDATION, INVALID_CODE, "Posição da opção começa em 1");
    }
  }

  public QuestionOption at(int newPosition) {
    return new QuestionOption(label, value, newPosition);
  }
}
