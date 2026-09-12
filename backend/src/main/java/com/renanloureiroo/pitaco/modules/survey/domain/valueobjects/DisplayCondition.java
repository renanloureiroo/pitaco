package com.renanloureiroo.pitaco.modules.survey.domain.valueobjects;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

// A forma da condição, sem olhar a pergunta de origem: quantos valores cada operador pede e se a
// faixa é coerente. Se a origem existe, vem antes e aceita esses valores, é a versão que decide —
// só ela conhece as outras perguntas.
public record DisplayCondition(
    QuestionKey sourceKey,
    ConditionOperator operator,
    List<String> values,
    Optional<Integer> min,
    Optional<Integer> max) {

  public static final int MAX_VALUES = 50;
  public static final int MAX_VALUE_LENGTH = 120;

  public DisplayCondition {
    if (sourceKey == null) {
      throw ConditionRejected.source("Informe a pergunta de origem da condição");
    }
    if (operator == null) {
      throw ConditionRejected.operator("Informe o operador da condição");
    }

    values =
        values == null
            ? List.of()
            : values.stream().map(value -> value == null ? "" : value.strip()).toList();
    min = min == null ? Optional.empty() : min;
    max = max == null ? Optional.empty() : max;

    if (values.stream().anyMatch(value -> value.isEmpty() || value.length() > MAX_VALUE_LENGTH)) {
      throw ConditionRejected.value(
          "Cada valor da condição precisa ter entre 1 e " + MAX_VALUE_LENGTH + " caracteres");
    }
    if (values.stream().distinct().count() != values.size()) {
      throw ConditionRejected.value("A condição não pode repetir o mesmo valor");
    }

    switch (operator) {
      case EQUALS, NOT_EQUALS -> {
        if (values.size() != 1 || min.isPresent() || max.isPresent()) {
          throw ConditionRejected.value("Igual e diferente comparam com exatamente um valor");
        }
      }
      case IN -> {
        if (values.isEmpty() || values.size() > MAX_VALUES || min.isPresent() || max.isPresent()) {
          throw ConditionRejected.value(
              "Dentro de um conjunto pede de 1 a " + MAX_VALUES + " valores");
        }
      }
      case BETWEEN -> {
        if (!values.isEmpty() || min.isEmpty() || max.isEmpty()) {
          throw ConditionRejected.value("Faixa numérica pede mínimo e máximo, e nenhum valor");
        }
        if (min.get() > max.get()) {
          throw ConditionRejected.value("O mínimo da faixa não pode passar do máximo");
        }
      }
    }

    values = List.copyOf(values);
  }

  public static DisplayCondition of(
      QuestionKey sourceKey, ConditionOperator operator, List<String> values) {
    return new DisplayCondition(sourceKey, operator, values, Optional.empty(), Optional.empty());
  }

  public static DisplayCondition between(QuestionKey sourceKey, int min, int max) {
    return new DisplayCondition(
        sourceKey, ConditionOperator.BETWEEN, List.of(), Optional.of(min), Optional.of(max));
  }

  public DisplayCondition pointingTo(QuestionKey newSource) {
    return new DisplayCondition(newSource, operator, values, min, max);
  }

  // Conjunto, não lista: a ordem em que os valores foram escritos não muda quem vê a pergunta.
  public String fingerprint() {
    return String.join(
        "|",
        sourceKey.value(),
        operator.name(),
        values.stream().sorted().collect(Collectors.joining(",")),
        min.map(String::valueOf).orElse("-"),
        max.map(String::valueOf).orElse("-"));
  }

  public boolean sameMeaningAs(DisplayCondition other) {
    return other != null && Objects.equals(fingerprint(), other.fingerprint());
  }
}
