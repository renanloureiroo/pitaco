package com.renanloureiroo.pitaco.modules.survey.application.errors;

import com.renanloureiroo.pitaco.core.error.ApplicationException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyId;

public final class SurveyNotPublished extends ApplicationException {

  private static final String CODE = "survey.not_published";

  private final SurveyId surveyId;

  public SurveyNotPublished(SurveyId surveyId) {
    super(ErrorType.BUSINESS_RULE, CODE, "A pesquisa ainda não foi publicada");
    this.surveyId = surveyId;
  }

  public SurveyId surveyId() {
    return surveyId;
  }
}
