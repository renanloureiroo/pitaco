package com.renanloureiroo.pitaco.modules.results.application.errors;

import com.renanloureiroo.pitaco.core.error.NotFoundException;

// Mesmo code da autoria e da coleta: para quem consome, é a mesma pesquisa que não existe.
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
