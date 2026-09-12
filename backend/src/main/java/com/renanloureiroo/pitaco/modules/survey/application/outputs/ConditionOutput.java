package com.renanloureiroo.pitaco.modules.survey.application.outputs;

import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.ConditionOperator;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.DisplayCondition;
import java.util.List;
import java.util.Optional;

public record ConditionOutput(
    String sourceKey,
    ConditionOperator operator,
    List<String> values,
    Optional<Integer> min,
    Optional<Integer> max) {

  public ConditionOutput {
    values = List.copyOf(values);
  }

  static ConditionOutput of(DisplayCondition condition) {
    return new ConditionOutput(
        condition.sourceKey().value(),
        condition.operator(),
        condition.values(),
        condition.min(),
        condition.max());
  }
}
