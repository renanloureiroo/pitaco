package com.renanloureiroo.pitaco.modules.collect.infra.http.presenters;

import com.renanloureiroo.pitaco.modules.collect.application.outputs.DeliverableQuestionOutput;
import com.renanloureiroo.pitaco.modules.collect.application.outputs.DeliverableSurveyOutput;
import com.renanloureiroo.pitaco.modules.collect.application.usecases.FindEligibleSurveyUseCase;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.EligibilityResponseDTO;

public final class EligibilityPresenter {

  private EligibilityPresenter() {}

  public static EligibilityResponseDTO present(FindEligibleSurveyUseCase.Output output) {
    return new EligibilityResponseDTO(
        output.survey().map(EligibilityPresenter::surveyOf).orElse(null));
  }

  private static EligibilityResponseDTO.SurveyDTO surveyOf(DeliverableSurveyOutput survey) {
    return new EligibilityResponseDTO.SurveyDTO(
        survey.surveyId(),
        survey.versionId(),
        survey.versionNumber(),
        survey.questions().stream().map(EligibilityPresenter::questionOf).toList());
  }

  private static EligibilityResponseDTO.QuestionDTO questionOf(DeliverableQuestionOutput question) {
    return new EligibilityResponseDTO.QuestionDTO(
        question.key(),
        question.position(),
        question.statement(),
        question.type(),
        question.required(),
        question.options().stream()
            .map(
                option ->
                    new EligibilityResponseDTO.OptionDTO(
                        option.label(), option.value(), option.position()))
            .toList(),
        question
            .range()
            .map(range -> new EligibilityResponseDTO.RangeDTO(range.min(), range.max()))
            .orElse(null));
  }
}
