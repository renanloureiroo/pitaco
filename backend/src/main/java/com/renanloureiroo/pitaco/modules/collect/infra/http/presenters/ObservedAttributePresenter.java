package com.renanloureiroo.pitaco.modules.collect.infra.http.presenters;

import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.modules.collect.application.outputs.ObservedAttributeOutput;
import com.renanloureiroo.pitaco.modules.collect.application.usecases.ListObservedAttributesUseCase;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.ObservedAttributeResponseDTO;

public final class ObservedAttributePresenter {

  private ObservedAttributePresenter() {}

  public static PageResponseDTO<ObservedAttributeResponseDTO> present(
      ListObservedAttributesUseCase.Output output) {
    return new PageResponseDTO<>(
        output.items().stream().map(ObservedAttributePresenter::present).toList(),
        output.page(),
        output.size(),
        output.total(),
        output.totalPages());
  }

  public static ObservedAttributeResponseDTO present(ObservedAttributeOutput output) {
    return new ObservedAttributeResponseDTO(
        output.name(),
        output.firstSeenAt(),
        output.lastSeenAt(),
        output.values().stream()
            .map(value -> new ObservedAttributeResponseDTO.Value(value.value(), value.lastSeenAt()))
            .toList());
  }
}
