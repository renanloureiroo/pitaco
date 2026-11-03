package com.renanloureiroo.pitaco.modules.survey.application.usecases;

import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.survey.application.outputs.StateTransitionOutput;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyStateTransitionRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.application.services.SurveyScope;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyState;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyStateTransition;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.TransitionReason;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.Trigger;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.TriggerWindow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ListStateTransitionsUseCase
    implements UseCase<ListStateTransitionsUseCase.Input, List<StateTransitionOutput>> {

  private final SurveyRepository surveysRepository;
  private final SurveyVersionRepository surveyVersionsRepository;
  private final SurveyStateTransitionRepository surveyStateTransitionsRepository;

  public ListStateTransitionsUseCase(
      SurveyRepository surveysRepository,
      SurveyVersionRepository surveyVersionsRepository,
      SurveyStateTransitionRepository surveyStateTransitionsRepository) {
    this.surveysRepository = surveysRepository;
    this.surveyVersionsRepository = surveyVersionsRepository;
    this.surveyStateTransitionsRepository = surveyStateTransitionsRepository;
  }

  // O histórico é a união do que foi comandado com o que a janela produziu. Nada é gravado aqui:
  // o instante da transição de janela é o limite dela, não o da leitura.
  @Override
  public List<StateTransitionOutput> execute(Input input) {
    var survey = SurveyScope.require(surveysRepository, input.applicationId(), input.surveyId());

    var history = new ArrayList<StateTransitionOutput>();
    surveyStateTransitionsRepository.findBySurveyId(survey.id()).stream()
        .map(ListStateTransitionsUseCase::viewOf)
        .forEach(history::add);

    surveyVersionsRepository
        .findPublished(survey.id())
        .flatMap(SurveyVersion::trigger)
        .map(Trigger::window)
        .ifPresent(window -> history.addAll(derivedFrom(window, Instant.now())));

    history.sort(Comparator.comparing(StateTransitionOutput::occurredAt));

    log.info(
        "Histórico de transições consultado survey={} total={}",
        survey.id().value(),
        history.size());

    return List.copyOf(history);
  }

  private static List<StateTransitionOutput> derivedFrom(TriggerWindow window, Instant now) {
    var derived = new ArrayList<StateTransitionOutput>();

    if (window.hasOpenedAt(now)) {
      derived.add(
          new StateTransitionOutput(
              SurveyState.SCHEDULED,
              SurveyState.ACTIVE,
              TransitionReason.WINDOW_OPENED,
              Optional.empty(),
              window.start()));
    }
    if (window.hasClosedAt(now)) {
      derived.add(
          new StateTransitionOutput(
              SurveyState.ACTIVE,
              SurveyState.ENDED,
              TransitionReason.WINDOW_CLOSED,
              Optional.empty(),
              window.end().orElseThrow()));
    }

    return derived;
  }

  private static StateTransitionOutput viewOf(SurveyStateTransition transition) {
    return new StateTransitionOutput(
        transition.getFrom(),
        transition.getTo(),
        transition.getReason(),
        transition.actor(),
        transition.getOccurredAt());
  }

  public record Input(String applicationId, String surveyId) {}
}
