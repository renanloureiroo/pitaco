package com.renanloureiroo.pitaco.modules.survey.domain.entities;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.catalog.QuestionOption;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.catalog.ScaleRange;
import com.renanloureiroo.pitaco.core.entity.Entity;
import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.DisplayCondition;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.QuestionStatement;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.ScaleLabels;
import java.util.List;
import java.util.Optional;
import lombok.Getter;

@Getter
public final class Question extends Entity<QuestionId> {

  private static final String OPTIONS_NOT_ALLOWED_CODE = "question.options_not_allowed";
  private static final String OPTIONS_DUPLICATED_CODE = "question.options_duplicated";
  private static final String RANGE_INVALID_CODE = "question.scale_range_invalid";
  private static final String POSITION_INVALID_CODE = "question.position_invalid";
  private static final String LABELS_NOT_ALLOWED_CODE = "question.scale_labels_not_allowed";

  private final QuestionKey key;
  private final QuestionStatement statement;
  private final QuestionType type;
  private final int position;
  private final boolean required;
  private final List<QuestionOption> options;
  private final ScaleRange range;
  private final ScaleLabels labels;
  private final DisplayCondition condition;

  // O que o autor informa ao montar ou reescrever uma pergunta: chave, identidade e posição
  // são do sistema, não dele.
  public record Draft(
      QuestionStatement statement,
      QuestionType type,
      boolean required,
      List<QuestionOption> options,
      Optional<ScaleRange> range,
      ScaleLabels labels,
      Optional<DisplayCondition> condition) {

    public Draft {
      options = List.copyOf(options);
      labels = labels == null ? ScaleLabels.none() : labels;
      condition = condition == null ? Optional.empty() : condition;
    }

    public Draft(
        QuestionStatement statement,
        QuestionType type,
        boolean required,
        List<QuestionOption> options,
        Optional<ScaleRange> range) {
      this(statement, type, required, options, range, ScaleLabels.none(), Optional.empty());
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
      Optional<ScaleRange> range,
      ScaleLabels labels,
      Optional<DisplayCondition> condition) {
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
    var declaredLabels = labels == null ? ScaleLabels.none() : labels;
    if (!declaredLabels.isEmpty() && !type.requiresRange()) {
      throw new DomainException(
          ErrorType.VALIDATION,
          LABELS_NOT_ALLOWED_CODE,
          "Só perguntas com escala numérica têm rótulos nos extremos");
    }

    this.key = key;
    this.statement = statement;
    this.type = type;
    this.position = position;
    this.required = required;
    this.options = List.copyOf(options);
    this.range = rangeFor(type, range);
    this.labels = declaredLabels;
    this.condition = condition.orElse(null);
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

    if (type.hasFixedRange()) {
      var fixed = type.fixedRange().orElseThrow();

      if (declared.isPresent() && !declared.get().equals(fixed)) {
        throw new DomainException(
            ErrorType.VALIDATION, RANGE_INVALID_CODE, "A faixa deste tipo é fixa e não pode mudar");
      }

      return fixed;
    }

    return declared.orElseThrow(
        () ->
            new DomainException(
                ErrorType.VALIDATION,
                RANGE_INVALID_CODE,
                "Pergunta deste tipo exige uma faixa numérica"));
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
        draft.range(),
        draft.labels(),
        draft.condition());
  }

  public static Question restore(
      QuestionId id,
      QuestionKey key,
      QuestionStatement statement,
      QuestionType type,
      int position,
      boolean required,
      List<QuestionOption> options,
      Optional<ScaleRange> range,
      ScaleLabels labels,
      Optional<DisplayCondition> condition) {
    return new Question(
        id, key, statement, type, position, required, options, range, labels, condition);
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
    return restore(
        id,
        key,
        statement,
        type,
        position,
        required,
        options,
        range,
        ScaleLabels.none(),
        Optional.empty());
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
        draft.range(),
        draft.labels(),
        draft.condition());
  }

  public Question movedTo(int newPosition) {
    return new Question(
        id(), key, statement, type, newPosition, required, options, range(), labels, condition());
  }

  // Cópia para a versão seguinte: a linhagem permanece, a identidade da linha é nova (D-07).
  public Question copyForNewVersion() {
    return new Question(
        QuestionId.generate(),
        key,
        statement,
        type,
        position,
        required,
        options,
        range(),
        labels,
        condition());
  }

  // Cópia para outra pesquisa: linhagem nova, porque não há resposta anterior a comparar. A
  // condição chega já apontando para a chave nova da origem.
  public Question duplicatedAs(QuestionKey newKey, Optional<DisplayCondition> remapped) {
    return new Question(
        QuestionId.generate(),
        newKey,
        statement,
        type,
        position,
        required,
        options,
        range(),
        labels,
        remapped);
  }

  public Optional<ScaleRange> range() {
    return Optional.ofNullable(range);
  }

  public Optional<DisplayCondition> condition() {
    return Optional.ofNullable(condition);
  }

  public boolean dependsOn(QuestionKey source) {
    return condition != null && condition.sourceKey().equals(source);
  }
}
