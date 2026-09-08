package com.renanloureiroo.pitaco.modules.survey.infra.http.presenters;

import com.renanloureiroo.pitaco.modules.survey.application.outputs.StateTransitionOutput;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyStateTransitionResponseDTO;
import java.util.List;
import java.util.Locale;

public final class ListStateTransitionsPresenter {

  private ListStateTransitionsPresenter() {}

  public static List<SurveyStateTransitionResponseDTO> present(
      List<StateTransitionOutput> transitions) {
    return transitions.stream()
        .map(
            transition ->
                new SurveyStateTransitionResponseDTO(
                    transition.from().name().toLowerCase(Locale.ROOT),
                    transition.to().name().toLowerCase(Locale.ROOT),
                    transition.reason().name().toLowerCase(Locale.ROOT),
                    transition.actor().orElse(null),
                    transition.occurredAt()))
        .toList();
  }
}
