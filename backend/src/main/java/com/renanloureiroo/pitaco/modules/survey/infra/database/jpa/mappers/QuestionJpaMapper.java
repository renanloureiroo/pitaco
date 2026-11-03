package com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.mappers;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.catalog.QuestionOption;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.catalog.ScaleRange;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.Question;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.QuestionId;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.ConditionOperator;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.DisplayCondition;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.QuestionStatement;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.ScaleLabels;
import com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.entities.QuestionJpaEntity;
import com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.entities.QuestionOptionJpaEntity;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public final class QuestionJpaMapper {

  private QuestionJpaMapper() {}

  public static QuestionJpaEntity toJpa(Question question) {
    var condition = question.condition();

    return new QuestionJpaEntity(
        question.id().value(),
        question.getKey().value(),
        question.getStatement().value(),
        question.getType().name(),
        question.getPosition(),
        question.isRequired(),
        question.range().map(ScaleRange::min).orElse(null),
        question.range().map(ScaleRange::max).orElse(null),
        question.getLabels().min().orElse(null),
        question.getLabels().max().orElse(null),
        condition.map(part -> part.sourceKey().value()).orElse(null),
        condition.map(part -> part.operator().name()).orElse(null),
        condition.flatMap(DisplayCondition::min).orElse(null),
        condition.flatMap(DisplayCondition::max).orElse(null),
        question.getOptions().stream()
            .map(option -> toJpa(question.id().value(), option))
            .collect(Collectors.toCollection(LinkedHashSet::new)),
        new ArrayList<>(condition.map(DisplayCondition::values).orElse(java.util.List.of())));
  }

  // A opção é value object: não tem identidade própria no domínio. A linha a recebe derivada
  // de (pergunta, valor) — que é exatamente o que uq_question_options_value já afirma ser único.
  // Um id sorteado a cada gravação faria toda regravação da versão virar delete + insert, e os
  // dois colidiriam na constraint dentro do mesmo flush.
  private static QuestionOptionJpaEntity toJpa(String questionId, QuestionOption option) {
    var id =
        UUID.nameUUIDFromBytes((questionId + "|" + option.value()).getBytes(StandardCharsets.UTF_8))
            .toString();

    return new QuestionOptionJpaEntity(id, option.position(), option.label(), option.value());
  }

  public static Question toDomain(QuestionJpaEntity entity) {
    var options =
        entity.getOptions().stream()
            .sorted(Comparator.comparingInt(QuestionOptionJpaEntity::getPosition))
            .map(
                option ->
                    new QuestionOption(option.getLabel(), option.getValue(), option.getPosition()))
            .toList();

    return Question.restore(
        QuestionId.of(entity.getId()),
        QuestionKey.of(entity.getQuestionKey()),
        QuestionStatement.of(entity.getStatement()),
        QuestionType.valueOf(entity.getType()),
        entity.getPosition(),
        entity.isRequired(),
        options,
        rangeOf(entity),
        ScaleLabels.of(entity.getRangeMinLabel(), entity.getRangeMaxLabel()),
        conditionOf(entity));
  }

  private static Optional<ScaleRange> rangeOf(QuestionJpaEntity entity) {
    if (entity.getRangeMin() == null || entity.getRangeMax() == null) {
      return Optional.empty();
    }
    return Optional.of(new ScaleRange(entity.getRangeMin(), entity.getRangeMax()));
  }

  private static Optional<DisplayCondition> conditionOf(QuestionJpaEntity entity) {
    if (entity.getConditionSourceKey() == null || entity.getConditionOperator() == null) {
      return Optional.empty();
    }
    return Optional.of(
        new DisplayCondition(
            QuestionKey.of(entity.getConditionSourceKey()),
            ConditionOperator.valueOf(entity.getConditionOperator()),
            entity.getConditionValues() == null
                ? java.util.List.of()
                : java.util.List.copyOf(entity.getConditionValues()),
            Optional.ofNullable(entity.getConditionMin()),
            Optional.ofNullable(entity.getConditionMax())));
  }
}
