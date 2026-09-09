package com.renanloureiroo.pitaco.testsupport.factories;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.AnswerRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.Answer;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.AnswerStatus;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayId;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.AnswerText;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.AnswerValue;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class AnswerFactory {

  public static final Instant DEFAULT_ANSWERED_AT = Instant.parse("2026-09-08T18:01:00Z");

  private DisplayId displayId = DisplayId.of(UUID.randomUUID().toString());
  private QuestionKey questionKey = QuestionKey.generate();
  private AnswerStatus status = AnswerStatus.ANSWERED;
  private AnswerValue value = new AnswerValue.NumericValue(9);
  private Instant answeredAt = DEFAULT_ANSWERED_AT;

  private AnswerFactory() {}

  public static AnswerFactory anAnswer() {
    return new AnswerFactory();
  }

  public AnswerFactory forDisplay(DisplayId displayId) {
    this.displayId = displayId;
    return this;
  }

  public AnswerFactory forQuestion(QuestionKey questionKey) {
    this.questionKey = questionKey;
    return this;
  }

  public AnswerFactory withText(String text) {
    this.status = AnswerStatus.ANSWERED;
    this.value = new AnswerValue.TextValue(AnswerText.of(text));
    return this;
  }

  public AnswerFactory withNumber(int number) {
    this.status = AnswerStatus.ANSWERED;
    this.value = new AnswerValue.NumericValue(number);
    return this;
  }

  public AnswerFactory withOptions(String... options) {
    this.status = AnswerStatus.ANSWERED;
    this.value = new AnswerValue.ChoiceValue(List.of(options));
    return this;
  }

  public AnswerFactory skipped() {
    this.status = AnswerStatus.SKIPPED;
    this.value = null;
    return this;
  }

  public AnswerFactory answeredAt(Instant answeredAt) {
    this.answeredAt = answeredAt;
    return this;
  }

  public Answer build() {
    return Answer.create(
        displayId, questionKey, status, Optional.ofNullable(value), answeredAt);
  }

  public Answer buildSavedIn(AnswerRepository repository) {
    var answer = build();
    repository.saveAll(List.of(answer));
    return answer;
  }
}
