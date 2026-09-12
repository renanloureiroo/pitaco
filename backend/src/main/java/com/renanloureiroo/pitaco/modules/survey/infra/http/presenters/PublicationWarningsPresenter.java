package com.renanloureiroo.pitaco.modules.survey.infra.http.presenters;

import com.renanloureiroo.pitaco.modules.survey.application.outputs.PublicationWarningOutput;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.CompetingSurveyDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.PublicationWarningDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.PublicationWarningsResponseDTO;
import java.util.List;

public final class PublicationWarningsPresenter {

  private PublicationWarningsPresenter() {}

  public static PublicationWarningsResponseDTO present(List<PublicationWarningOutput> warnings) {
    return new PublicationWarningsResponseDTO(
        warnings.stream().map(PublicationWarningsPresenter::present).toList());
  }

  private static PublicationWarningDTO present(PublicationWarningOutput warning) {
    return new PublicationWarningDTO(
        warning.code(),
        warning.ruleId().orElse(null),
        warning.attribute().orElse(null),
        warning.competingSurveys().isEmpty()
            ? null
            : warning.competingSurveys().stream()
                .map(
                    survey ->
                        new CompetingSurveyDTO(survey.surveyId(), survey.name(), survey.priority()))
                .toList(),
        warning.minRequiredVersion().orElse(null),
        warning.unsupportedShare().orElse(null));
  }
}
