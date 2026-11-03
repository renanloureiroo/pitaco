package com.renanloureiroo.pitaco.modules.survey.application.errors;

import com.renanloureiroo.pitaco.core.error.ApplicationException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.identity.SurveyId;

// Não há versão em rascunho para receber a escrita: o conteúdo publicado está congelado.
public final class SurveyContentFrozen extends ApplicationException {

  private static final String CODE = "survey.content_frozen";

  private final SurveyId surveyId;

  public SurveyContentFrozen(SurveyId surveyId) {
    super(
        ErrorType.BUSINESS_RULE,
        CODE,
        "O conteúdo desta pesquisa está publicado; abra uma nova versão para alterá-lo");
    this.surveyId = surveyId;
  }

  public SurveyId surveyId() {
    return surveyId;
  }
}
