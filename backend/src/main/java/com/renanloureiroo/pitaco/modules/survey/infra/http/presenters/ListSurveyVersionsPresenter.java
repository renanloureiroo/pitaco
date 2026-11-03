package com.renanloureiroo.pitaco.modules.survey.infra.http.presenters;

import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.ListSurveyVersionsUseCase;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyVersionResponseDTO;

public final class ListSurveyVersionsPresenter {

  private ListSurveyVersionsPresenter() {}

  public static PageResponseDTO<SurveyVersionResponseDTO> present(
      ListSurveyVersionsUseCase.Output output) {
    return new PageResponseDTO<>(
        output.items().stream().map(SurveyVersionPresenter::present).toList(),
        output.page(),
        output.size(),
        output.total(),
        output.totalPages());
  }
}
