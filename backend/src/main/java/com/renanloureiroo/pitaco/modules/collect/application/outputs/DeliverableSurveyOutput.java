package com.renanloureiroo.pitaco.modules.collect.application.outputs;

import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog.DeliverableSurvey;
import java.util.List;

// A versão inteira em uma resposta só: o SDK renderiza sem segunda chamada (FR-019).
public record DeliverableSurveyOutput(
    String surveyId,
    String versionId,
    int versionNumber,
    List<DeliverableQuestionOutput> questions) {

  public DeliverableSurveyOutput {
    questions = List.copyOf(questions);
  }

  public static DeliverableSurveyOutput of(DeliverableSurvey survey) {
    return new DeliverableSurveyOutput(
        survey.surveyId().value(),
        survey.versionId().value(),
        survey.versionNumber(),
        survey.questions().stream().map(DeliverableQuestionOutput::of).toList());
  }
}
