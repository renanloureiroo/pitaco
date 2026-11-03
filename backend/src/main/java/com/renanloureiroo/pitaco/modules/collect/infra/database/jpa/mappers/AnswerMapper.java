package com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.mappers;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.Answer;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.AnswerId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.AnswerStatus;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayId;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.AnswerText;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.AnswerValue;
import com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.entities.SurveyAnswerJpaEntity;
import com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.entities.SurveyAnswerOptionJpaEntity;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

public final class AnswerMapper {

  private AnswerMapper() {}

  public static SurveyAnswerJpaEntity toJpa(Answer answer) {
    var entity =
        new SurveyAnswerJpaEntity(
            answer.id().value(),
            answer.getDisplayId().value(),
            answer.getQuestionKey().value(),
            answer.getStatus().name(),
            null,
            null,
            answer.getAnsweredAt(),
            new LinkedHashSet<>());

    answer
        .value()
        .ifPresent(
            value -> {
              switch (value) {
                case AnswerValue.TextValue text -> entity.setTextValue(text.text().value());
                case AnswerValue.NumericValue number -> entity.setNumericValue(number.value());
                case AnswerValue.ChoiceValue choice -> entity.setOptions(optionsOf(choice));
              }
            });

    return entity;
  }

  public static Answer toDomain(SurveyAnswerJpaEntity entity) {
    return Answer.restore(
        AnswerId.of(entity.getId()),
        DisplayId.of(entity.getDisplayId()),
        QuestionKey.of(entity.getQuestionKey()),
        AnswerStatus.valueOf(entity.getStatus()),
        valueOf(entity),
        entity.getAnsweredAt());
  }

  private static Set<SurveyAnswerOptionJpaEntity> optionsOf(AnswerValue.ChoiceValue choice) {
    var options = new LinkedHashSet<SurveyAnswerOptionJpaEntity>();
    for (var position = 0; position < choice.options().size(); position++) {
      options.add(
          new SurveyAnswerOptionJpaEntity(choice.options().get(position), position + 1));
    }
    return options;
  }

  private static Optional<AnswerValue> valueOf(SurveyAnswerJpaEntity entity) {
    if (entity.getTextValue() != null) {
      return Optional.of(new AnswerValue.TextValue(AnswerText.of(entity.getTextValue())));
    }
    if (entity.getNumericValue() != null) {
      return Optional.of(new AnswerValue.NumericValue(entity.getNumericValue()));
    }
    if (!entity.getOptions().isEmpty()) {
      return Optional.of(
          new AnswerValue.ChoiceValue(
              entity.getOptions().stream()
                  .sorted(java.util.Comparator.comparingInt(SurveyAnswerOptionJpaEntity::getPosition))
                  .map(SurveyAnswerOptionJpaEntity::getOptionValue)
                  .toList()));
    }
    return Optional.empty();
  }
}
