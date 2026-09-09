package com.renanloureiroo.pitaco.modules.collect.infra.http.presenters;

import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.modules.collect.application.outputs.RespondentDisplaySummaryOutput;
import com.renanloureiroo.pitaco.modules.collect.application.usecases.ListRespondentDisplaysUseCase;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.RespondentDisplayResponseDTO;

public final class RespondentDisplayPresenter {

  private RespondentDisplayPresenter() {}

  public static PageResponseDTO<RespondentDisplayResponseDTO> present(
      ListRespondentDisplaysUseCase.Output output) {
    return new PageResponseDTO<RespondentDisplayResponseDTO>(
        output.items().stream().map(RespondentDisplayPresenter::present).toList(),
        output.page(),
        output.size(),
        output.total(),
        output.totalPages());
  }

  public static RespondentDisplayResponseDTO present(RespondentDisplaySummaryOutput output) {
    var display = output.display();

    return new RespondentDisplayResponseDTO(
        display.id().value(),
        output.surveyId().value(),
        display.versionId().value(),
        display.versionNumber(),
        display.comparabilityGroup(),
        display.outcome(),
        display.sdkVersion().orElse(null),
        display.openedAt(),
        display.closedAt().orElse(null));
  }
}
