package com.renanloureiroo.pitaco.modules.survey.application.errors;

import com.renanloureiroo.pitaco.core.error.NotFoundException;

public final class SurveyVersionNotFound extends NotFoundException {

  private static final String CODE = "survey_version.not_found";

  private final String surveyId;

  public SurveyVersionNotFound(String surveyId) {
    super(CODE, "Versão de pesquisa não encontrada");
    this.surveyId = surveyId;
  }

  public String surveyId() {
    return surveyId;
  }
}
