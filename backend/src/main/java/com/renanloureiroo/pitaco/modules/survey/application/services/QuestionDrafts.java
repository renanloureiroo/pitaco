package com.renanloureiroo.pitaco.modules.survey.application.services;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.catalog.QuestionOption;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.catalog.ScaleRange;
import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.Question;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.ConditionOperator;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.ConditionRejected;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.DisplayCondition;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.QuestionStatement;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.ScaleLabels;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Acrescentar e reescrever recebem exatamente o mesmo conteúdo; a tradução dos dados crus para
// os value objects mora aqui em vez de nos dois casos de uso.
public final class QuestionDrafts {

  private QuestionDrafts() {}

  public record Draft(
      String statement,
      QuestionType type,
      boolean required,
      List<OptionInput> options,
      Optional<RangeInput> range,
      Optional<ConditionInput> condition) {

    public Draft {
      options = List.copyOf(options);
      condition = condition == null ? Optional.empty() : condition;
    }

    public Draft(
        String statement,
        QuestionType type,
        boolean required,
        List<OptionInput> options,
        Optional<RangeInput> range) {
      this(statement, type, required, options, range, Optional.empty());
    }
  }

  public record OptionInput(String label, String value) {}

  public record RangeInput(int min, int max, Optional<String> minLabel, Optional<String> maxLabel) {

    public RangeInput {
      minLabel = minLabel == null ? Optional.empty() : minLabel;
      maxLabel = maxLabel == null ? Optional.empty() : maxLabel;
    }

    public RangeInput(int min, int max) {
      this(min, max, Optional.empty(), Optional.empty());
    }
  }

  public record ConditionInput(
      String sourceKey,
      ConditionOperator operator,
      List<String> values,
      Optional<Integer> min,
      Optional<Integer> max) {}

  public static Question.Draft from(Draft draft) {
    var options = new ArrayList<QuestionOption>();
    for (var index = 0; index < draft.options().size(); index++) {
      var option = draft.options().get(index);
      options.add(new QuestionOption(option.label(), option.value(), index + 1));
    }

    return new Question.Draft(
        QuestionStatement.of(draft.statement()),
        draft.type(),
        draft.required(),
        List.copyOf(options),
        draft.range().map(range -> new ScaleRange(range.min(), range.max())),
        draft
            .range()
            .map(range -> new ScaleLabels(range.minLabel(), range.maxLabel()))
            .orElse(ScaleLabels.none()),
        draft.condition().map(QuestionDrafts::conditionOf));
  }

  private static DisplayCondition conditionOf(ConditionInput input) {
    return new DisplayCondition(
        sourceKeyOf(input.sourceKey()), input.operator(), input.values(), input.min(), input.max());
  }

  // Chave malformada é origem que não existe: o painel mostra junto do campo, como as outras.
  private static QuestionKey sourceKeyOf(String value) {
    try {
      return QuestionKey.of(value);
    } catch (DomainException malformed) {
      throw ConditionRejected.source("A pergunta de origem não existe nesta versão");
    }
  }
}
