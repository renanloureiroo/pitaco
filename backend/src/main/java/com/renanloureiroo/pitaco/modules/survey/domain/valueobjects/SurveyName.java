package com.renanloureiroo.pitaco.modules.survey.domain.valueobjects;

import com.renanloureiroo.pitaco.core.text.RequiredText;

public record SurveyName(String value) {

  private static final int MAX_LENGTH = 120;

  private static final String INVALID_CODE = "survey.name_invalid";

  public SurveyName {
    value = RequiredText.of(value, MAX_LENGTH, INVALID_CODE, "Nome");
  }

  public static SurveyName of(String value) {
    return new SurveyName(value);
  }

  // O nome padrão da duplicação cabe no limite mesmo quando o original já está nele: corta o
  // fim em vez de recusar uma cópia que o autor não nomeou.
  public SurveyName copy() {
    var copied = "Cópia de " + value;
    return new SurveyName(
        copied.length() <= MAX_LENGTH ? copied : copied.substring(0, MAX_LENGTH).strip());
  }

  @Override
  public String toString() {
    return value;
  }
}
