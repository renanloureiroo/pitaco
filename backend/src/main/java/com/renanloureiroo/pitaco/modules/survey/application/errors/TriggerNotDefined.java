package com.renanloureiroo.pitaco.modules.survey.application.errors;

import com.renanloureiroo.pitaco.core.error.ApplicationException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.identity.SurveyId;

public final class TriggerNotDefined extends ApplicationException {

  private static final String CODE = "trigger.not_defined";

  private final SurveyId surveyId;

  public TriggerNotDefined(SurveyId surveyId) {
    super(
        ErrorType.BUSINESS_RULE,
        CODE,
        "Defina o disparo antes de acrescentar regras de segmentação");
    this.surveyId = surveyId;
  }

  public SurveyId surveyId() {
    return surveyId;
  }
}
