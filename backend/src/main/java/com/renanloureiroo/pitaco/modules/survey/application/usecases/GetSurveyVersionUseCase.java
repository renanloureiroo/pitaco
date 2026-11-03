package com.renanloureiroo.pitaco.modules.survey.application.usecases;

import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.survey.application.errors.SurveyVersionNotFound;
import com.renanloureiroo.pitaco.modules.survey.application.outputs.SurveyVersionDetailOutput;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.application.services.SurveyScope;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GetSurveyVersionUseCase
    implements UseCase<GetSurveyVersionUseCase.Input, SurveyVersionDetailOutput> {

  private final SurveyRepository surveysRepository;
  private final SurveyVersionRepository surveyVersionsRepository;

  public GetSurveyVersionUseCase(
      SurveyRepository surveysRepository, SurveyVersionRepository surveyVersionsRepository) {
    this.surveysRepository = surveysRepository;
    this.surveyVersionsRepository = surveyVersionsRepository;
  }

  @Override
  public SurveyVersionDetailOutput execute(Input input) {
    var survey = SurveyScope.require(surveysRepository, input.applicationId(), input.surveyId());

    var version =
        surveyVersionsRepository
            .findByNumber(survey.id(), input.number())
            .orElseThrow(() -> new SurveyVersionNotFound(survey.id().value()));

    log.info("Versão consultada survey={} version={}", survey.id().value(), version.getNumber());

    return SurveyVersionDetailOutput.of(version);
  }

  public record Input(String applicationId, String surveyId, int number) {}
}
