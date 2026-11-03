package com.renanloureiroo.pitaco.modules.survey.infra.http.presenters;

import com.renanloureiroo.pitaco.modules.survey.application.outputs.SurveyVersionDetailOutput;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyVersionDetailResponseDTO;

public final class GetSurveyVersionPresenter {

  private GetSurveyVersionPresenter() {}

  public static SurveyVersionDetailResponseDTO present(SurveyVersionDetailOutput output) {
    var version = SurveyVersionPresenter.present(output.version());

    return new SurveyVersionDetailResponseDTO(
        version.id(),
        version.number(),
        version.status(),
        version.publishedAt(),
        version.changeKind(),
        version.changeSummary(),
        version.comparabilityGroup(),
        QuestionPresenter.presentAll(output.questions()),
        output.trigger().map(TriggerPresenter::present).orElse(null));
  }
}
