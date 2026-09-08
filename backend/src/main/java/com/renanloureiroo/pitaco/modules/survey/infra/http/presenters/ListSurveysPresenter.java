package com.renanloureiroo.pitaco.modules.survey.infra.http.presenters;

import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.ListSurveysUseCase;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyResponseDTO;

public final class ListSurveysPresenter {

  private ListSurveysPresenter() {}

  public static PageResponseDTO<SurveyResponseDTO> present(ListSurveysUseCase.Output output) {
    return new PageResponseDTO<>(
        output.items().stream().map(SurveyPresenter::present).toList(),
        output.page(),
        output.size(),
        output.total(),
        output.totalPages());
  }
}
