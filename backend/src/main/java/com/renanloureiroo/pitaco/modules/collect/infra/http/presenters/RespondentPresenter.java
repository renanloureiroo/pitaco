package com.renanloureiroo.pitaco.modules.collect.infra.http.presenters;

import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.modules.collect.application.outputs.RespondentOutput;
import com.renanloureiroo.pitaco.modules.collect.application.usecases.ListRespondentsUseCase;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.RespondentResponseDTO;

public final class RespondentPresenter {

  private RespondentPresenter() {}

  public static PageResponseDTO<RespondentResponseDTO> present(
      ListRespondentsUseCase.Output output) {
    return new PageResponseDTO<>(
        output.items().stream().map(RespondentPresenter::present).toList(),
        output.page(),
        output.size(),
        output.total(),
        output.totalPages());
  }

  public static RespondentResponseDTO present(RespondentOutput output) {
    return new RespondentResponseDTO(
        output.id().value(),
        output.identityKind(),
        output.identityValue(),
        output.firstSeenAt(),
        output.lastSeenAt());
  }
}
