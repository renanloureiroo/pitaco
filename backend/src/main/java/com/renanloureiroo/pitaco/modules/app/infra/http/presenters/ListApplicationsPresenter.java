package com.renanloureiroo.pitaco.modules.app.infra.http.presenters;

import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.modules.app.application.usecases.ListApplicationsUseCase;
import com.renanloureiroo.pitaco.modules.app.infra.http.dtos.ApplicationSummaryResponseDTO;
import java.util.Locale;

public final class ListApplicationsPresenter {

  private ListApplicationsPresenter() {}

  public static PageResponseDTO<ApplicationSummaryResponseDTO> present(
      ListApplicationsUseCase.Output output) {
    return new PageResponseDTO<>(
        output.items().stream().map(ListApplicationsPresenter::itemOf).toList(),
        output.page(),
        output.size(),
        output.total(),
        output.totalPages());
  }

  private static ApplicationSummaryResponseDTO itemOf(ListApplicationsUseCase.Item item) {
    return new ApplicationSummaryResponseDTO(
        item.id(),
        item.slug(),
        item.name(),
        item.status().name().toLowerCase(Locale.ROOT),
        item.createdAt());
  }
}
