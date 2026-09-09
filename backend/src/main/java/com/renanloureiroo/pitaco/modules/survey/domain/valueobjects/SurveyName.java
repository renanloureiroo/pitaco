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

  @Override
  public String toString() {
    return value;
  }
}
