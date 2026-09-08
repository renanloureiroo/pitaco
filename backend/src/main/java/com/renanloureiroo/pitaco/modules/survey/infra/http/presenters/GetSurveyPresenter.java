package com.renanloureiroo.pitaco.modules.survey.infra.http.presenters;

import com.renanloureiroo.pitaco.modules.survey.application.outputs.SurveyDetailOutput;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyDetailResponseDTO;
import java.util.Locale;

public final class GetSurveyPresenter {

  private GetSurveyPresenter() {}

  public static SurveyDetailResponseDTO present(SurveyDetailOutput output) {
    var survey = SurveyPresenter.present(output.survey());

    return new SurveyDetailResponseDTO(
        survey.id(),
        survey.applicationId(),
        survey.name(),
        survey.state(),
        survey.publishedVersionNumber(),
        survey.draftVersionNumber(),
        survey.createdAt(),
        output.content().map(GetSurveyPresenter::contentOf).orElse(null));
  }

  private static SurveyDetailResponseDTO.Content contentOf(
      SurveyDetailOutput.ContentOutput content) {
    return new SurveyDetailResponseDTO.Content(
        content.source().name().toLowerCase(Locale.ROOT),
        content.versionNumber(),
        QuestionPresenter.presentAll(content.questions()),
        content.trigger().map(TriggerPresenter::present).orElse(null));
  }
}
