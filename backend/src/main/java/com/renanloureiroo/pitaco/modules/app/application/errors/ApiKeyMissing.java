package com.renanloureiroo.pitaco.modules.app.application.errors;

import com.renanloureiroo.pitaco.core.error.UnauthorizedException;

public final class ApiKeyMissing extends UnauthorizedException {

  private static final String CODE = "api_key.missing";

  public ApiKeyMissing() {
    super(CODE, "A chave da aplicação é obrigatória nesta superfície");
  }
}
