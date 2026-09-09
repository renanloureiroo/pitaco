package com.renanloureiroo.pitaco.modules.survey.application.errors;

import com.renanloureiroo.pitaco.core.error.ConflictException;
import com.renanloureiroo.pitaco.core.identity.SurveyId;

public final class SurveyDraftVersionAlreadyOpen extends ConflictException {

  private static final String CODE = "survey_version.draft_already_open";

  private final SurveyId surveyId;

  public SurveyDraftVersionAlreadyOpen(SurveyId surveyId) {
    super(CODE, "Já existe um rascunho de versão aberto nesta pesquisa");
    this.surveyId = surveyId;
  }

  public SurveyId surveyId() {
    return surveyId;
  }
}
