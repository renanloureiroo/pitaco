package com.renanloureiroo.pitaco.modules.survey.application.repositories;

import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyStateTransition;
import java.util.List;

public interface SurveyStateTransitionRepository {

  SurveyStateTransition record(SurveyStateTransition transition);

  // Ordenadas por occurredAt: só as comandadas moram aqui.
  List<SurveyStateTransition> findBySurveyId(SurveyId surveyId);
}
