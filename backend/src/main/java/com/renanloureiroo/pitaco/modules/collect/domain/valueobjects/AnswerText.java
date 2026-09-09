package com.renanloureiroo.pitaco.modules.collect.domain.valueobjects;

import com.renanloureiroo.pitaco.core.text.RequiredText;

public record AnswerText(String value) {

  public static final int MAX_LENGTH = 2000;

  private static final String INVALID_CODE = "answer.text_invalid";

  public AnswerText {
    value = RequiredText.of(value, MAX_LENGTH, INVALID_CODE, "Texto da resposta");
  }

  public static AnswerText of(String value) {
    return new AnswerText(value);
  }
}
