package com.renanloureiroo.pitaco.modules.survey.application.errors;

import com.renanloureiroo.pitaco.core.error.ApplicationException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.identity.SurveyId;

public final class PublishedSurveyCannotBeDiscarded extends ApplicationException {

  private static final String CODE = "survey.published_cannot_be_discarded";

  private final SurveyId surveyId;

  public PublishedSurveyCannotBeDiscarded(SurveyId surveyId) {
    super(ErrorType.BUSINESS_RULE, CODE, "Pesquisa já publicada não se apaga, se encerra");
    this.surveyId = surveyId;
  }

  public SurveyId surveyId() {
    return surveyId;
  }
}
