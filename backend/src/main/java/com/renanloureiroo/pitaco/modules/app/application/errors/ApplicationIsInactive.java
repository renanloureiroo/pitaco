package com.renanloureiroo.pitaco.modules.app.application.errors;

import com.renanloureiroo.pitaco.core.error.ApplicationException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApplicationId;

public final class ApplicationIsInactive extends ApplicationException {

  private static final String CODE = "application.inactive";

  private final ApplicationId applicationId;

  public ApplicationIsInactive(ApplicationId applicationId) {
    super(ErrorType.BUSINESS_RULE, CODE, "Aplicação inativa não pode emitir chaves");
    this.applicationId = applicationId;
  }

  public ApplicationId applicationId() {
    return applicationId;
  }
}
