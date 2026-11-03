package com.renanloureiroo.pitaco.modules.app.application.errors;

import com.renanloureiroo.pitaco.core.error.UnauthorizedException;

// Chave desconhecida e chave revogada dão a mesma resposta: distinguir as duas contaria a quem
// tenta que a chave existiu (US1.6).
public final class ApiKeyInvalid extends UnauthorizedException {

  private static final String CODE = "api_key.invalid";

  public ApiKeyInvalid() {
    super(CODE, "Chave da aplicação inválida");
  }
}
