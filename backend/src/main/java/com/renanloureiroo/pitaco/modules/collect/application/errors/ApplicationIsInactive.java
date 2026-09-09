package com.renanloureiroo.pitaco.modules.collect.application.errors;

import com.renanloureiroo.pitaco.core.error.ApplicationException;
import com.renanloureiroo.pitaco.core.error.ErrorType;

// Reescrita aqui, e não importada de modules/app: dois módulos nunca se conhecem pelo domínio, e
// o que os dois compartilham é o `code`, que é contrato público, não a classe.
public final class ApplicationIsInactive extends ApplicationException {

  private static final String CODE = "application.inactive";

  public ApplicationIsInactive() {
    super(ErrorType.BUSINESS_RULE, CODE, "A aplicação está inativa e não aceita coleta");
  }
}
