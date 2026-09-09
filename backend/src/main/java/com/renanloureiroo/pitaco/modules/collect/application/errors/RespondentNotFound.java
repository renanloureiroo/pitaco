package com.renanloureiroo.pitaco.modules.collect.application.errors;

import com.renanloureiroo.pitaco.core.error.NotFoundException;

public final class RespondentNotFound extends NotFoundException {

  private static final String CODE = "respondent.not_found";

  private final String respondentId;

  public RespondentNotFound(String respondentId) {
    super(CODE, "Respondente não encontrado");
    this.respondentId = respondentId;
  }

  public String respondentId() {
    return respondentId;
  }
}
