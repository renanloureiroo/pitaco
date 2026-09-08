package com.renanloureiroo.pitaco.modules.survey.domain.valueobjects;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;

public record QuestionOption(String label, String value, int position) {

  private static final int MAX_LABEL_LENGTH = 200;
  private static final int MAX_VALUE_LENGTH = 120;

  private static final String INVALID_CODE = "question.option_invalid";

  public QuestionOption {
    label = required(label, "Rótulo da opção", MAX_LABEL_LENGTH);
    value = required(value, "Valor da opção", MAX_VALUE_LENGTH);
    if (position < 1) {
      throw new DomainException(ErrorType.VALIDATION, INVALID_CODE, "Posição da opção começa em 1");
    }
  }

  private static String required(String text, String subject, int maxLength) {
    if (text == null || text.isBlank()) {
      throw new DomainException(ErrorType.VALIDATION, INVALID_CODE, subject + " é obrigatório");
    }
    var stripped = text.strip();
    if (stripped.length() > maxLength) {
      throw new DomainException(
          ErrorType.VALIDATION,
          INVALID_CODE,
          subject + " não pode passar de " + maxLength + " caracteres");
    }
    return stripped;
  }

  public QuestionOption at(int newPosition) {
    return new QuestionOption(label, value, newPosition);
  }
}
