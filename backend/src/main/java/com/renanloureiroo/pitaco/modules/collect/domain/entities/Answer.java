package com.renanloureiroo.pitaco.modules.collect.domain.entities;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.entity.Entity;
import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.AnswerValue;
import java.time.Instant;
import java.util.Optional;
import lombok.Getter;

// Crua e imutável: esta feature não edita nem apaga resposta.
@Getter
public final class Answer extends Entity<AnswerId> {

  private static final String INVALID_CODE = "answer.invalid";

  private final DisplayId displayId;
  private final QuestionKey questionKey;
  private final AnswerStatus status;
  private final AnswerValue value;
  private final Instant answeredAt;

  private Answer(
      AnswerId id,
      DisplayId displayId,
      QuestionKey questionKey,
      AnswerStatus status,
      Optional<AnswerValue> value,
      Instant answeredAt) {
    super(id);

    require(displayId != null, "Resposta precisa pertencer a uma exibição");
    require(questionKey != null, "Resposta precisa apontar uma pergunta");
    require(status != null, "Resposta precisa de uma situação");
    require(answeredAt != null, "Resposta precisa do instante em que foi dada");
    require(
        (status == AnswerStatus.ANSWERED) == value.isPresent(),
        "O valor existe se e somente se a pergunta foi respondida");

    this.displayId = displayId;
    this.questionKey = questionKey;
    this.status = status;
    this.value = value.orElse(null);
    this.answeredAt = answeredAt;
  }

  public static Answer create(
      DisplayId displayId,
      QuestionKey questionKey,
      AnswerStatus status,
      Optional<AnswerValue> value,
      Instant answeredAt) {
    return new Answer(AnswerId.generate(), displayId, questionKey, status, value, answeredAt);
  }

  public static Answer restore(
      AnswerId id,
      DisplayId displayId,
      QuestionKey questionKey,
      AnswerStatus status,
      Optional<AnswerValue> value,
      Instant answeredAt) {
    return new Answer(id, displayId, questionKey, status, value, answeredAt);
  }

  public Optional<AnswerValue> value() {
    return Optional.ofNullable(value);
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new DomainException(ErrorType.VALIDATION, INVALID_CODE, message);
    }
  }
}
