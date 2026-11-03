package com.renanloureiroo.pitaco.modules.collect.infra.http.presenters;

import com.renanloureiroo.pitaco.modules.collect.application.usecases.ListSdkVersionsUseCase;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.SdkVersionsResponseDTO;

public final class SdkVersionPresenter {

  private SdkVersionPresenter() {}

  public static SdkVersionsResponseDTO present(ListSdkVersionsUseCase.Output output) {
    return new SdkVersionsResponseDTO(
        output.recentFrom(),
        output.recentRequests(),
        output.versions().stream()
            .map(
                version ->
                    new SdkVersionsResponseDTO.Version(
                        version.version(),
                        version.requestCount(),
                        version.recentRequestCount(),
                        version.recentShare().orElse(null),
                        version.firstSeenAt(),
                        version.lastSeenAt(),
                        version.stale()))
            .toList());
  }
}
