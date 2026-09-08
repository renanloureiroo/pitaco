package com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.mappers;

import com.renanloureiroo.pitaco.modules.survey.domain.entities.Question;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.QuestionId;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.QuestionType;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.QuestionKey;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.QuestionOption;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.QuestionStatement;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.ScaleRange;
import com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.entities.QuestionJpaEntity;
import com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.entities.QuestionOptionJpaEntity;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public final class QuestionJpaMapper {

  private QuestionJpaMapper() {}

  public static QuestionJpaEntity toJpa(Question question) {
    return new QuestionJpaEntity(
        question.id().value(),
        question.getKey().value(),
        question.getStatement().value(),
        question.getType().name(),
        question.getPosition(),
        question.isRequired(),
        question.range().map(ScaleRange::min).orElse(null),
        question.range().map(ScaleRange::max).orElse(null),
        question.getOptions().stream()
            .map(option -> toJpa(question.id().value(), option))
            .collect(Collectors.toCollection(LinkedHashSet::new)));
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
        rangeOf(entity));
  }

  private static Optional<ScaleRange> rangeOf(QuestionJpaEntity entity) {
    if (entity.getRangeMin() == null || entity.getRangeMax() == null) {
      return Optional.empty();
    }
    return Optional.of(new ScaleRange(entity.getRangeMin(), entity.getRangeMax()));
  }
}
