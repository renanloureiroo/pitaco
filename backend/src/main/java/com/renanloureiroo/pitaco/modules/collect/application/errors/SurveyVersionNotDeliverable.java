package com.renanloureiroo.pitaco.modules.collect.application.errors;

import com.renanloureiroo.pitaco.core.error.NotFoundException;

// Inexistente, em rascunho e de outra aplicação são indistinguíveis por fora: dizer qual é
// contaria a quem pergunta o que existe na conta de outro (SC-012).
public final class SurveyVersionNotDeliverable extends NotFoundException {

  private static final String CODE = "survey_version.not_found";

  public SurveyVersionNotDeliverable() {
    super(CODE, "Versão de pesquisa não encontrada");
  }
}
