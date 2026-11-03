package com.renanloureiroo.pitaco.modules.survey.infra.http.presenters;

import com.renanloureiroo.pitaco.modules.survey.application.usecases.GetVersionComparabilityUseCase;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.VersionComparabilityResponseDTO;

public final class VersionComparabilityPresenter {

  private VersionComparabilityPresenter() {}

  public static VersionComparabilityResponseDTO present(
      GetVersionComparabilityUseCase.Output output) {
    return new VersionComparabilityResponseDTO(
        output.groups().stream()
            .map(
                group -> new VersionComparabilityResponseDTO.Group(group.group(), group.versions()))
            .toList());
  }
}
