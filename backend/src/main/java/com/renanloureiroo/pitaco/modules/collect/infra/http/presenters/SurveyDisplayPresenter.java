package com.renanloureiroo.pitaco.modules.collect.infra.http.presenters;

import com.renanloureiroo.pitaco.modules.collect.application.outputs.SurveyDisplayOutput;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.SurveyDisplayResponseDTO;

public final class SurveyDisplayPresenter {

  private SurveyDisplayPresenter() {}

  public static SurveyDisplayResponseDTO present(SurveyDisplayOutput output) {
    return new SurveyDisplayResponseDTO(
        output.displayId(),
        output.surveyId(),
        output.versionId(),
        output.outcome(),
        output.openedAt());
  }
}
