package com.renanloureiroo.pitaco.modules.survey.application.errors;

import com.renanloureiroo.pitaco.core.error.ConflictException;
import com.renanloureiroo.pitaco.core.identity.SurveyId;

public final class SurveyAlreadyPublished extends ConflictException {

  private static final String CODE = "survey.already_published";

  private final SurveyId surveyId;

  public SurveyAlreadyPublished(SurveyId surveyId) {
    super(CODE, "A pesquisa já está publicada e não tem rascunho de versão aberto");
    this.surveyId = surveyId;
  }

  public SurveyId surveyId() {
    return surveyId;
  }
}
