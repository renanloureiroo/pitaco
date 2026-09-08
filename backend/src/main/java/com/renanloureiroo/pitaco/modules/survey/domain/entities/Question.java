package com.renanloureiroo.pitaco.modules.survey.domain.entities;

import com.renanloureiroo.pitaco.core.entity.Entity;
import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.QuestionKey;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.QuestionOption;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.QuestionStatement;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.ScaleRange;
import java.util.List;
import java.util.Optional;
import lombok.Getter;

@Getter
public final class Question extends Entity<QuestionId> {

  private static final String OPTIONS_NOT_ALLOWED_CODE = "question.options_not_allowed";
  private static final String OPTIONS_DUPLICATED_CODE = "question.options_duplicated";
  private static final String RANGE_INVALID_CODE = "question.scale_range_invalid";
  private static final String POSITION_INVALID_CODE = "question.position_invalid";

  private final QuestionKey key;
  private final QuestionStatement statement;
  private final QuestionType type;
  private final int position;
  private final boolean required;
  private final List<QuestionOption> options;
  private final ScaleRange range;

  // O que o autor informa ao montar ou reescrever uma pergunta: chave, identidade e posição
  // são do sistema, não dele.
  public record Draft(
      QuestionStatement statement,
      QuestionType type,
      boolean required,
      List<QuestionOption> options,
      Optional<ScaleRange> range) {

    public Draft {
      options = List.copyOf(options);
    }
  }

  private Question(
      QuestionId id,
      QuestionKey key,
      QuestionStatement statement,
      QuestionType type,
      int position,
      boolean required,
      List<QuestionOption> options,
      Optional<ScaleRange> range) {
    super(id);

    if (position < 1) {
      throw new DomainException(
          ErrorType.VALIDATION, POSITION_INVALID_CODE, "Posição da pergunta começa em 1");
    }
    if (!options.isEmpty() && !type.acceptsOptions()) {
      throw new DomainException(
          ErrorType.VALIDATION,
          OPTIONS_NOT_ALLOWED_CODE,
          "Pergunta deste tipo não aceita opções de resposta");
    }
    if (options.stream().map(QuestionOption::value).distinct().count() != options.size()) {
      throw new DomainException(
          ErrorType.VALIDATION,
          OPTIONS_DUPLICATED_CODE,
          "Duas opções da mesma pergunta não podem ter o mesmo valor");
    }

    this.key = key;
    this.statement = statement;
    this.type = type;
    this.position = position;
    this.required = required;
    this.options = List.copyOf(options);
    this.range = rangeFor(type, range);
  }

  // Coerência da faixa, não completude: exigida onde o tipo pede, recusada onde não cabe, e
  // imposta onde o catálogo já a fixa.
  private static ScaleRange rangeFor(QuestionType type, Optional<ScaleRange> declared) {
    if (!type.requiresRange()) {
      if (declared.isPresent()) {
        throw new DomainException(
            ErrorType.VALIDATION,
            RANGE_INVALID_CODE,
            "Pergunta deste tipo não aceita faixa numérica");
      }
      return null;
    }

    var range =
        declared.orElseThrow(
            () ->
                new DomainException(
                    ErrorType.VALIDATION,
                    RANGE_INVALID_CODE,
                    "Pergunta deste tipo exige uma faixa numérica"));

    if (type.hasFixedRange() && !type.fixedRange().orElseThrow().equals(range)) {
      throw new DomainException(
          ErrorType.VALIDATION, RANGE_INVALID_CODE, "A faixa deste tipo é fixa e não pode mudar");
    }

    return range;
  }

  public static Question create(Draft draft, int position) {
    return new Question(
        QuestionId.generate(),
        QuestionKey.generate(),
        draft.statement(),
        draft.type(),
        position,
        draft.required(),
        draft.options(),
        draft.range());
  }

  public static Question restore(
      QuestionId id,
      QuestionKey key,
      QuestionStatement statement,
      QuestionType type,
      int position,
      boolean required,
      List<QuestionOption> options,
      Optional<ScaleRange> range) {
    return new Question(id, key, statement, type, position, required, options, range);
  }

  public Question rewrittenAs(Draft draft) {
    return new Question(
        id(),
        key,
        draft.statement(),
        draft.type(),
        position,
        draft.required(),
        draft.options(),
        draft.range());
  }

  public Question movedTo(int newPosition) {
    return new Question(id(), key, statement, type, newPosition, required, options, range());
  }

  // Cópia para a versão seguinte: a linhagem permanece, a identidade da linha é nova (D-07).
  public Question copyForNewVersion() {
    return new Question(
        QuestionId.generate(), key, statement, type, position, required, options, range());
  }

  public Optional<ScaleRange> range() {
    return Optional.ofNullable(range);
  }
}
