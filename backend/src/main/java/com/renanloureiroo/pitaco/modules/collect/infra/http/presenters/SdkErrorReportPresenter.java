package com.renanloureiroo.pitaco.modules.collect.infra.http.presenters;

import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.modules.collect.application.outputs.SdkErrorReportOutput;
import com.renanloureiroo.pitaco.modules.collect.application.usecases.ListSdkErrorsUseCase;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.SdkErrorReportResponseDTO;

public final class SdkErrorReportPresenter {

  private SdkErrorReportPresenter() {}

  public static PageResponseDTO<SdkErrorReportResponseDTO> present(
      ListSdkErrorsUseCase.Output output) {
    return new PageResponseDTO<>(
        output.items().stream().map(SdkErrorReportPresenter::present).toList(),
        output.page(),
        output.size(),
        output.total(),
        output.totalPages());
  }

  public static SdkErrorReportResponseDTO present(SdkErrorReportOutput output) {
    return new SdkErrorReportResponseDTO(
        output.id(),
        output.sdkVersion().orElse(null),
        output.kind(),
        output.message(),
        output.context(),
        output.occurredAt(),
        output.receivedAt());
  }
}
