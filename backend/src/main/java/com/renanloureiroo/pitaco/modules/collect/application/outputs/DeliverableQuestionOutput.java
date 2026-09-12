package com.renanloureiroo.pitaco.modules.collect.application.outputs;

import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog.DeliverableQuestion;
import java.util.List;
import java.util.Optional;

public record DeliverableQuestionOutput(
    String key,
    int position,
    String statement,
    QuestionType type,
    boolean required,
    List<OptionOutput> options,
    Optional<RangeOutput> range,
    Optional<ConditionOutput> condition) {

  public record OptionOutput(String label, String value, int position) {}

  public record RangeOutput(
      int min, int max, Optional<String> minLabel, Optional<String> maxLabel) {}

  public record ConditionOutput(
      String sourceKey,
      String operator,
      List<String> values,
      Optional<Integer> min,
      Optional<Integer> max) {}

  public DeliverableQuestionOutput {
    options = List.copyOf(options);
    condition = condition == null ? Optional.empty() : condition;
  }

  public static DeliverableQuestionOutput of(DeliverableQuestion question) {
    return new DeliverableQuestionOutput(
        question.key().value(),
        question.position(),
        question.statement(),
        question.type(),
        question.required(),
        question.options().stream()
            .map(option -> new OptionOutput(option.label(), option.value(), option.position()))
            .toList(),
        question
            .range()
            .map(
                range ->
                    new RangeOutput(
                        range.min(), range.max(), question.minLabel(), question.maxLabel())),
        question
            .condition()
            .map(
                condition ->
                    new ConditionOutput(
                        condition.sourceKey().value(),
                        condition.operator(),
                        condition.values(),
                        condition.min(),
                        condition.max())));
  }
}
