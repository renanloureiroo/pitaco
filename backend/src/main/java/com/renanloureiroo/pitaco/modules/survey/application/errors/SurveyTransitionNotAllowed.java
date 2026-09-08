package com.renanloureiroo.pitaco.modules.survey.application.errors;

import com.renanloureiroo.pitaco.core.error.ApplicationException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyId;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyLifecycle;

public final class SurveyTransitionNotAllowed extends ApplicationException {

  private static final String CODE = "survey.transition_not_allowed";

  private final SurveyId surveyId;
  private final SurveyLifecycle lifecycle;

  public SurveyTransitionNotAllowed(SurveyId surveyId, SurveyLifecycle lifecycle) {
    super(
        ErrorType.BUSINESS_RULE,
        CODE,
        "A pesquisa não admite esta transição a partir do estado atual");
    this.surveyId = surveyId;
    this.lifecycle = lifecycle;
  }

  public SurveyId surveyId() {
    return surveyId;
  }

  public SurveyLifecycle lifecycle() {
    return lifecycle;
  }
}
