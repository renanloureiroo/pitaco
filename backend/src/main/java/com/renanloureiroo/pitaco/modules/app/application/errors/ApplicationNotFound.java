package com.renanloureiroo.pitaco.modules.app.application.errors;

import com.renanloureiroo.pitaco.core.error.NotFoundException;

public final class ApplicationNotFound extends NotFoundException {

  private static final String CODE = "application.not_found";

  private final String applicationId;

  public ApplicationNotFound(String applicationId) {
    super(CODE, "Aplicação não encontrada");
    this.applicationId = applicationId;
  }

  public String applicationId() {
    return applicationId;
  }
}
