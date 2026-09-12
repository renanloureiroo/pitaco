package com.renanloureiroo.pitaco.modules.survey.application.usecases;

import com.renanloureiroo.pitaco.core.transaction.Transactional;
import com.renanloureiroo.pitaco.core.usecase.UseCaseWithoutOutput;
import com.renanloureiroo.pitaco.modules.survey.application.errors.PublishedSurveyCannotBeDiscarded;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.application.services.SurveyScope;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DiscardSurveyUseCase implements UseCaseWithoutOutput<DiscardSurveyUseCase.Input> {

  private final SurveyRepository surveysRepository;
  private final SurveyVersionRepository surveyVersionsRepository;

  public DiscardSurveyUseCase(
      SurveyRepository surveysRepository, SurveyVersionRepository surveyVersionsRepository) {
    this.surveysRepository = surveysRepository;
    this.surveyVersionsRepository = surveyVersionsRepository;
  }

  @Override
  @Transactional
  public void execute(Input input) {
    var survey = SurveyScope.requireLocked(surveysRepository, input.applicationId(), input.surveyId());

    if (survey.isPublished()) {
      throw new PublishedSurveyCannotBeDiscarded(survey.id());
    }

    surveyVersionsRepository.deleteBySurveyId(survey.id());
    surveysRepository.delete(survey.id());

    log.info("Pesquisa descartada [{}] application={}", survey.id().value(), input.applicationId());
  }

  public record Input(String applicationId, String surveyId) {}
}
