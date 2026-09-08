package com.renanloureiroo.pitaco.modules.survey.application.outputs;

import com.renanloureiroo.pitaco.modules.survey.domain.entities.Question;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.QuestionType;
import java.util.List;
import java.util.Optional;

public record QuestionOutput(
    String id,
    String key,
    String statement,
    QuestionType type,
    int position,
    boolean required,
    List<QuestionOptionOutput> options,
    Optional<ScaleRangeOutput> range) {

  public QuestionOutput {
    options = List.copyOf(options);
  }

  public static QuestionOutput of(Question question) {
    return new QuestionOutput(
        question.id().value(),
        question.getKey().value(),
        question.getStatement().value(),
        question.getType(),
        question.getPosition(),
        question.isRequired(),
        question.getOptions().stream().map(QuestionOptionOutput::of).toList(),
        question.range().map(ScaleRangeOutput::of));
  }

  public static List<QuestionOutput> ofAll(List<Question> questions) {
    return questions.stream().map(QuestionOutput::of).toList();
  }
}
