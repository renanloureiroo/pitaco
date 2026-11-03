package com.renanloureiroo.pitaco.modules.survey.application.errors;

import com.renanloureiroo.pitaco.core.error.NotFoundException;

// Identificador malformado, pesquisa inexistente e pesquisa de outra aplicação respondem os
// três a mesma coisa: distinguir entregaria um oráculo de existência a quem sonda a API.
public final class SurveyNotFound extends NotFoundException {

  private static final String CODE = "survey.not_found";

  private final String surveyId;

  public SurveyNotFound(String surveyId) {
    super(CODE, "Pesquisa não encontrada");
    this.surveyId = surveyId;
  }

  public String surveyId() {
    return surveyId;
  }
}
