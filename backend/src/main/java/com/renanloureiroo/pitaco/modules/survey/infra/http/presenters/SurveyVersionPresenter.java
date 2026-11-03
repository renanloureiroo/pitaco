package com.renanloureiroo.pitaco.modules.survey.infra.http.presenters;

import com.renanloureiroo.pitaco.modules.survey.application.outputs.SurveyVersionOutput;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyVersionResponseDTO;
import java.util.Locale;

public final class SurveyVersionPresenter {

  private SurveyVersionPresenter() {}

  public static SurveyVersionResponseDTO present(SurveyVersionOutput output) {
    return new SurveyVersionResponseDTO(
        output.id().value(),
        output.number(),
        output.status().name().toLowerCase(Locale.ROOT),
        output.publishedAt().orElse(null),
        output.changeKind().map(kind -> kind.name().toLowerCase(Locale.ROOT)).orElse(null),
        output.changeSummary().orElse(null),
        output.comparabilityGroup());
  }
}
