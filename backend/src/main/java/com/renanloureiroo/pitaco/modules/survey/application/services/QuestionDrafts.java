package com.renanloureiroo.pitaco.modules.survey.application.services;

import com.renanloureiroo.pitaco.modules.survey.domain.entities.Question;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.catalog.QuestionOption;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.QuestionStatement;
import com.renanloureiroo.pitaco.core.catalog.ScaleRange;
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
      Optional<RangeInput> range) {

    public Draft {
      options = List.copyOf(options);
    }
  }

  public record OptionInput(String label, String value) {}

  public record RangeInput(int min, int max) {}

  public static Question.Draft from(Draft draft) {
    var options = new java.util.ArrayList<QuestionOption>();
    for (var index = 0; index < draft.options().size(); index++) {
      var option = draft.options().get(index);
      options.add(new QuestionOption(option.label(), option.value(), index + 1));
    }

    return new Question.Draft(
        QuestionStatement.of(draft.statement()),
        draft.type(),
        draft.required(),
        List.copyOf(options),
        draft.range().map(range -> new ScaleRange(range.min(), range.max())));
  }
}
