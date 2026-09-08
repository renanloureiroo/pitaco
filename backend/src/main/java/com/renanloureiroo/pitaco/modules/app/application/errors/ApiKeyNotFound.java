package com.renanloureiroo.pitaco.modules.app.application.errors;

import com.renanloureiroo.pitaco.core.error.NotFoundException;

public final class ApiKeyNotFound extends NotFoundException {

  private static final String CODE = "api_key.not_found";

  private final String apiKeyId;

  public ApiKeyNotFound(String apiKeyId) {
    super(CODE, "Chave de API não encontrada");
    this.apiKeyId = apiKeyId;
  }

  public String apiKeyId() {
    return apiKeyId;
  }
}
