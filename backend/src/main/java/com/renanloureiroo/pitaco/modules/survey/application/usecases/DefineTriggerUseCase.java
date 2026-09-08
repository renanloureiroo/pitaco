package com.renanloureiroo.pitaco.modules.survey.application.usecases;

import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.survey.application.outputs.TriggerOutput;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.application.services.SurveyScope;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.EventName;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SamplingRate;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.Trigger;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.TriggerWindow;
import java.time.Instant;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DefineTriggerUseCase implements UseCase<DefineTriggerUseCase.Input, TriggerOutput> {

  private final SurveyRepository surveysRepository;
  private final SurveyVersionRepository surveyVersionsRepository;

  public DefineTriggerUseCase(
      SurveyRepository surveysRepository, SurveyVersionRepository surveyVersionsRepository) {
    this.surveysRepository = surveysRepository;
    this.surveyVersionsRepository = surveyVersionsRepository;
  }

  @Override
  public TriggerOutput execute(Input input) {
    var survey = SurveyScope.require(surveysRepository, input.applicationId(), input.surveyId());
    var version = SurveyScope.requireEditableVersion(surveyVersionsRepository, survey);

    version.defineTrigger(
        new Trigger(
            EventName.of(input.eventName()),
            new TriggerWindow(input.windowStart(), input.windowEnd()),
            SamplingRate.of(input.samplingRate())));
    surveyVersionsRepository.update(version);

    log.info(
        "Disparo definido survey={} version={} event={}",
        survey.id().value(),
        version.getNumber(),
        input.eventName());

    return TriggerOutput.of(version.trigger().orElseThrow(), version.getRules());
  }

  public record Input(
      String applicationId,
      String surveyId,
      String eventName,
      Instant windowStart,
      Optional<Instant> windowEnd,
      double samplingRate) {}
}
