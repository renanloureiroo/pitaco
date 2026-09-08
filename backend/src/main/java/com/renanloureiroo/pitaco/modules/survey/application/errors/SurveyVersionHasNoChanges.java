package com.renanloureiroo.pitaco.modules.survey.application.errors;

import com.renanloureiroo.pitaco.core.error.ApplicationException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyId;

public final class SurveyVersionHasNoChanges extends ApplicationException {

  private static final String CODE = "survey_version.no_changes";

  private final SurveyId surveyId;

  public SurveyVersionHasNoChanges(SurveyId surveyId) {
    super(
        ErrorType.BUSINESS_RULE,
        CODE,
        "O rascunho é idêntico à versão publicada: não há o que publicar");
    this.surveyId = surveyId;
  }

  public SurveyId surveyId() {
    return surveyId;
  }
}
