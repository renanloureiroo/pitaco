package com.renanloureiroo.pitaco.modules.app.infra.http.presenters;

import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.modules.app.application.usecases.ListApiKeysUseCase;
import com.renanloureiroo.pitaco.modules.app.infra.http.dtos.ApiKeyResponseDTO;
import java.util.Locale;

public final class ListApiKeysPresenter {

  private ListApiKeysPresenter() {}

  public static PageResponseDTO<ApiKeyResponseDTO> present(ListApiKeysUseCase.Output output) {
    return new PageResponseDTO<>(
        output.items().stream().map(ListApiKeysPresenter::itemOf).toList(),
        output.page(),
        output.size(),
        output.total(),
        output.totalPages());
  }

  private static ApiKeyResponseDTO itemOf(ListApiKeysUseCase.Item item) {
    return new ApiKeyResponseDTO(
        item.id(),
        item.applicationId(),
        item.label(),
        item.prefix(),
        item.status().name().toLowerCase(Locale.ROOT),
        item.createdAt(),
        item.revokedAt().orElse(null));
  }
}
