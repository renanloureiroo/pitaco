package com.renanloureiroo.pitaco.modules.survey.application.usecases;

import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.survey.application.outputs.SurveyOutput;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.application.services.SurveyScope;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SurveyName;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.Trigger;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RenameSurveyUseCase implements UseCase<RenameSurveyUseCase.Input, SurveyOutput> {

  private final SurveyRepository surveysRepository;
  private final SurveyVersionRepository surveyVersionsRepository;

  public RenameSurveyUseCase(
      SurveyRepository surveysRepository, SurveyVersionRepository surveyVersionsRepository) {
    this.surveysRepository = surveysRepository;
    this.surveyVersionsRepository = surveyVersionsRepository;
  }

  @Override
  public SurveyOutput execute(Input input) {
    var survey = SurveyScope.require(surveysRepository, input.applicationId(), input.surveyId());

    survey.rename(SurveyName.of(input.name()));
    surveysRepository.update(survey);

    var window =
        surveyVersionsRepository
            .findPublished(survey.id())
            .flatMap(SurveyVersion::trigger)
            .map(Trigger::window);

    log.info("Pesquisa renomeada [{}] application={}", survey.id().value(), input.applicationId());

    return SurveyOutput.of(survey, survey.stateAt(Instant.now(), window));
  }

  public record Input(String applicationId, String surveyId, String name) {}
}
