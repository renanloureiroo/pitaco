package com.renanloureiroo.pitaco.modules.collect.application.errors;

import com.renanloureiroo.pitaco.core.error.NotFoundException;

// O code é o mesmo que a autoria publica: para quem consome, é a mesma pesquisa que não existe
// (D-05). Inexistente, malformada e de outra aplicação são indistinguíveis por fora.
public final class SurveyNotFoundInApplication extends NotFoundException {

  private static final String CODE = "survey.not_found";

  private final String surveyId;

  public SurveyNotFoundInApplication(String surveyId) {
    super(CODE, "Pesquisa não encontrada");
    this.surveyId = surveyId;
  }

  public String surveyId() {
    return surveyId;
  }
}
