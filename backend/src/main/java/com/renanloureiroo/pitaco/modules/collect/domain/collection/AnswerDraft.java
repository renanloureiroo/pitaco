package com.renanloureiroo.pitaco.modules.collect.domain.collection;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.AnswerStatus;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.AnswerText;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.AnswerValue;
import java.util.List;
import java.util.Optional;

public record AnswerDraft(
    QuestionKey questionKey, AnswerStatus status, Optional<RawAnswerValue> value) {

  // Só depois que a validação passou: aqui os value objects já não podem recusar nada.
  public Optional<AnswerValue> asAnswerValue(QuestionType type) {
    if (status != AnswerStatus.ANSWERED) {
      return Optional.empty();
    }

    return value.map(
        raw ->
            switch (raw) {
              case RawAnswerValue.RawText text ->
                  type == QuestionType.FREE_TEXT
                      ? new AnswerValue.TextValue(AnswerText.of(text.value()))
                      : new AnswerValue.ChoiceValue(List.of(text.value()));
              case RawAnswerValue.RawNumber number -> new AnswerValue.NumericValue(number.value());
              case RawAnswerValue.RawChoices choices ->
                  new AnswerValue.ChoiceValue(choices.values());
            });
  }
}
