package com.renanloureiroo.pitaco.modules.collect.infra.http.presenters;

import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.modules.collect.application.outputs.DisplaySummaryOutput;
import com.renanloureiroo.pitaco.modules.collect.application.usecases.ListSurveyDisplaysUseCase;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.DisplaySummaryResponseDTO;

public final class DisplaySummaryPresenter {

  private DisplaySummaryPresenter() {}

  public static PageResponseDTO<DisplaySummaryResponseDTO> present(
      ListSurveyDisplaysUseCase.Output output) {
    return new PageResponseDTO<>(
        output.items().stream().map(DisplaySummaryPresenter::present).toList(),
        output.page(),
        output.size(),
        output.total(),
        output.totalPages());
  }

  public static DisplaySummaryResponseDTO present(DisplaySummaryOutput output) {
    return new DisplaySummaryResponseDTO(
        output.id().value(),
        output.versionId().value(),
        output.versionNumber(),
        output.comparabilityGroup(),
        output.outcome(),
        output.sdkVersion().orElse(null),
        output.openedAt(),
        output.closedAt().orElse(null));
  }
}
