package com.renanloureiroo.pitaco.modules.survey.application.usecases;

import com.renanloureiroo.pitaco.core.transaction.Transactional;
import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.survey.application.errors.SurveyNotPublished;
import com.renanloureiroo.pitaco.modules.survey.application.errors.SurveyTransitionNotAllowed;
import com.renanloureiroo.pitaco.modules.survey.application.outputs.SurveyOutput;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyStateTransitionRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.application.services.SurveyScope;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyLifecycle;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyStateTransition;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.TransitionReason;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.Trigger;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EndSurveyUseCase implements UseCase<EndSurveyUseCase.Input, SurveyOutput> {

  private final SurveyRepository surveysRepository;
  private final SurveyVersionRepository surveyVersionsRepository;
  private final SurveyStateTransitionRepository surveyStateTransitionsRepository;

  public EndSurveyUseCase(
      SurveyRepository surveysRepository,
      SurveyVersionRepository surveyVersionsRepository,
      SurveyStateTransitionRepository surveyStateTransitionsRepository) {
    this.surveysRepository = surveysRepository;
    this.surveyVersionsRepository = surveyVersionsRepository;
    this.surveyStateTransitionsRepository = surveyStateTransitionsRepository;
  }

  @Override
  @Transactional
  public SurveyOutput execute(Input input) {
    var survey = SurveyScope.require(surveysRepository, input.applicationId(), input.surveyId());

    if (survey.getLifecycle() == SurveyLifecycle.DRAFT) {
      throw new SurveyNotPublished(survey.id());
    }
    if (List.of(SurveyLifecycle.ENDED).contains(survey.getLifecycle())) {
      throw new SurveyTransitionNotAllowed(survey.id(), survey.getLifecycle());
    }

    var window =
        surveyVersionsRepository
            .findPublished(survey.id())
            .flatMap(SurveyVersion::trigger)
            .map(Trigger::window);
    var now = Instant.now();
    var before = survey.stateAt(now, window);

    survey.end();

    surveysRepository.update(survey);
    surveyStateTransitionsRepository.record(
        SurveyStateTransition.record(
            survey.id(), before, survey.stateAt(now, window), TransitionReason.MANUAL_END, now));

    log.info("Pesquisa encerrada [{}] application={}", survey.id().value(), input.applicationId());

    return SurveyOutput.of(survey, survey.stateAt(now, window));
  }

  public record Input(String applicationId, String surveyId) {}
}
