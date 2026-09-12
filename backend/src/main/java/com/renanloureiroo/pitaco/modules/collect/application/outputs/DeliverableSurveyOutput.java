package com.renanloureiroo.pitaco.modules.collect.application.outputs;

import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog.DeliverableSurvey;
import java.util.List;
import java.util.Optional;

// A versão inteira em uma resposta só: o SDK renderiza sem segunda chamada (FR-019).
public record DeliverableSurveyOutput(
    String surveyId,
    String versionId,
    int versionNumber,
    List<DeliverableQuestionOutput> questions,
    FreeTextNoticeOutput freeTextNotice) {

  public record FreeTextNoticeOutput(boolean enabled, Optional<String> text) {}

  public DeliverableSurveyOutput {
    questions = List.copyOf(questions);
  }

  public static DeliverableSurveyOutput of(DeliverableSurvey survey) {
    return new DeliverableSurveyOutput(
        survey.surveyId().value(),
        survey.versionId().value(),
        survey.versionNumber(),
        survey.questions().stream().map(DeliverableQuestionOutput::of).toList(),
        new FreeTextNoticeOutput(survey.freeTextNotice().isPresent(), survey.freeTextNotice()));
  }
}
