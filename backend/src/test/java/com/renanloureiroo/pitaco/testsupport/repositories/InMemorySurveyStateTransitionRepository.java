package com.renanloureiroo.pitaco.testsupport.repositories;

import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyStateTransitionRepository;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyId;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyStateTransition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class InMemorySurveyStateTransitionRepository implements SurveyStateTransitionRepository {

  private final List<SurveyStateTransition> transitions = new ArrayList<>();

  @Override
  public SurveyStateTransition record(SurveyStateTransition transition) {
    transitions.add(transition);
    return transition;
  }

  @Override
  public List<SurveyStateTransition> findBySurveyId(SurveyId surveyId) {
    return transitions.stream()
        .filter(transition -> transition.getSurveyId().equals(surveyId))
        .sorted(Comparator.comparing(SurveyStateTransition::getOccurredAt))
        .toList();
  }

  public List<SurveyStateTransition> findAll() {
    return List.copyOf(transitions);
  }

  public boolean isEmpty() {
    return transitions.isEmpty();
  }
}
