package com.renanloureiroo.pitaco.modules.survey.application.outputs;

import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.Question;
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
    Optional<ScaleRangeOutput> range,
    Optional<ConditionOutput> condition) {

  public QuestionOutput {
    options = List.copyOf(options);
    condition = condition == null ? Optional.empty() : condition;
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
        question.range().map(range -> ScaleRangeOutput.of(range, question.getLabels())),
        question.condition().map(ConditionOutput::of));
  }

  public static List<QuestionOutput> ofAll(List<Question> questions) {
    return questions.stream().map(QuestionOutput::of).toList();
  }
}
