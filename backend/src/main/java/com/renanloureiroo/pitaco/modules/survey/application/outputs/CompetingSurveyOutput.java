package com.renanloureiroo.pitaco.modules.survey.application.outputs;

import com.renanloureiroo.pitaco.modules.survey.domain.publication.CompetingSurvey;

public record CompetingSurveyOutput(String surveyId, String name, int priority) {

  public static CompetingSurveyOutput of(CompetingSurvey survey) {
    return new CompetingSurveyOutput(survey.surveyId().value(), survey.name(), survey.priority());
  }
}
