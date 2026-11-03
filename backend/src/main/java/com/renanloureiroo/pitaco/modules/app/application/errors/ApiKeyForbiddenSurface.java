package com.renanloureiroo.pitaco.modules.app.application.errors;

import com.renanloureiroo.pitaco.core.error.ForbiddenException;

// Falha barulhento em vez de ignorar em silêncio: a chave do SDK não vale no painel (FR-003).
public final class ApiKeyForbiddenSurface extends ForbiddenException {

  private static final String CODE = "api_key.forbidden_surface";

  public ApiKeyForbiddenSurface() {
    super(CODE, "A chave da aplicação não vale na superfície administrativa");
  }
}
