package com.renanloureiroo.pitaco.modules.survey.application.outputs;

import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import java.util.List;
import java.util.Optional;

public record SurveyVersionDetailOutput(
    SurveyVersionOutput version, List<QuestionOutput> questions, Optional<TriggerOutput> trigger) {

  public SurveyVersionDetailOutput {
    questions = List.copyOf(questions);
  }

  public static SurveyVersionDetailOutput of(SurveyVersion version) {
    return new SurveyVersionDetailOutput(
        SurveyVersionOutput.of(version),
        QuestionOutput.ofAll(version.getQuestions()),
        version.trigger().map(trigger -> TriggerOutput.of(trigger, version.getRules())));
  }
}
