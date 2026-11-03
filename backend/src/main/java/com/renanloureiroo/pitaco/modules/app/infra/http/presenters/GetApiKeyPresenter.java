package com.renanloureiroo.pitaco.modules.app.infra.http.presenters;

import com.renanloureiroo.pitaco.modules.app.application.usecases.GetApiKeyUseCase;
import com.renanloureiroo.pitaco.modules.app.infra.http.dtos.ApiKeyResponseDTO;
import java.util.Locale;

public final class GetApiKeyPresenter {

  private GetApiKeyPresenter() {}

  public static ApiKeyResponseDTO present(GetApiKeyUseCase.Output output) {
    return new ApiKeyResponseDTO(
        output.id(),
        output.applicationId(),
        output.label(),
        output.prefix(),
        output.status().name().toLowerCase(Locale.ROOT),
        output.createdAt(),
        output.revokedAt().orElse(null));
  }
}
