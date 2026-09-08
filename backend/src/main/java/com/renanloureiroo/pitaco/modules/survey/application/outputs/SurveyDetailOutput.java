package com.renanloureiroo.pitaco.modules.survey.application.outputs;

import java.util.List;
import java.util.Optional;

public record SurveyDetailOutput(SurveyOutput survey, Optional<ContentOutput> content) {

  public enum ContentSource {
    DRAFT,
    PUBLISHED
  }

  public record ContentOutput(
      ContentSource source,
      int versionNumber,
      List<QuestionOutput> questions,
      Optional<TriggerOutput> trigger) {

    public ContentOutput {
      questions = List.copyOf(questions);
    }
  }
}
