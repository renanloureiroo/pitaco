package com.renanloureiroo.pitaco.modules.collect.infra.http.presenters;

import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.modules.collect.application.outputs.ObservedEventOutput;
import com.renanloureiroo.pitaco.modules.collect.application.usecases.ListObservedEventsUseCase;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.ObservedEventResponseDTO;

public final class ObservedEventPresenter {

  private ObservedEventPresenter() {}

  public static PageResponseDTO<ObservedEventResponseDTO> present(
      ListObservedEventsUseCase.Output output) {
    return new PageResponseDTO<>(
        output.items().stream().map(ObservedEventPresenter::present).toList(),
        output.page(),
        output.size(),
        output.total(),
        output.totalPages());
  }

  public static ObservedEventResponseDTO present(ObservedEventOutput output) {
    return new ObservedEventResponseDTO(output.name(), output.firstSeenAt(), output.lastSeenAt());
  }
}
