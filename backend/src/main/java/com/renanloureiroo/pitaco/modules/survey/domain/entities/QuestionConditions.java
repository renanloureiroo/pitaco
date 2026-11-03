package com.renanloureiroo.pitaco.modules.survey.domain.entities;

import com.renanloureiroo.pitaco.core.catalog.QuestionOption;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.ConditionOperator;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.ConditionRejected;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.DisplayCondition;
import java.util.List;
import java.util.stream.Collectors;

// O que a condição exige das outras perguntas da versão. A origem vem antes da condicionada,
// então referência circular é impossível por construção — não há ciclo com a seta sempre para
// trás.
final class QuestionConditions {

  private QuestionConditions() {}

  static void check(Question target, List<Question> questions) {
    target.condition().ifPresent(condition -> check(condition, target.getPosition(), questions));
  }

  // A mudança numa origem não pode quebrar em silêncio quem depende dela: a recusa aponta a
  // dependente, e o autor decide se ajusta a condição ou desiste da mudança.
  static void requireDependentsStillValid(Question source, List<Question> questions) {
    for (var question : questions) {
      if (!question.dependsOn(source.getKey())) {
        continue;
      }
      try {
        check(question, questions);
      } catch (ConditionRejected broken) {
        throw ConditionRejected.sourceInUse(question.getKey());
      }
    }
  }

  static void requireNoDependents(Question source, List<Question> questions) {
    questions.stream()
        .filter(question -> question.dependsOn(source.getKey()))
        .findFirst()
        .ifPresent(
            dependent -> {
              throw ConditionRejected.sourceInUse(dependent.getKey());
            });
  }

  static void requireSourcesFirst(List<Question> ordered) {
    for (var question : ordered) {
      question
          .condition()
          .ifPresent(
              condition -> {
                var sourcePosition =
                    ordered.stream()
                        .filter(candidate -> candidate.getKey().equals(condition.sourceKey()))
                        .findFirst()
                        .map(Question::getPosition)
                        .orElse(Integer.MAX_VALUE);
                if (sourcePosition >= question.getPosition()) {
                  throw ConditionRejected.orderInvalid(question.getKey());
                }
              });
    }
  }

  private static void check(DisplayCondition condition, int position, List<Question> questions) {
    var source =
        questions.stream()
            .filter(question -> question.getKey().equals(condition.sourceKey()))
            .findFirst()
            .orElseThrow(
                () -> ConditionRejected.source("A pergunta de origem não existe nesta versão"));

    if (source.getPosition() >= position) {
      throw ConditionRejected.source("A condição só pode olhar para uma pergunta anterior");
    }

    switch (source.getType()) {
      case FREE_TEXT ->
          throw ConditionRejected.source("Texto livre não pode ser origem de condição");
      case SINGLE_CHOICE, MULTIPLE_CHOICE -> checkChoice(condition, source);
      case RATING, SCALE, NPS -> checkNumeric(condition, source);
    }
  }

  private static void checkChoice(DisplayCondition condition, Question source) {
    if (condition.operator() == ConditionOperator.BETWEEN) {
      throw ConditionRejected.operator("Faixa numérica só vale para perguntas de escala");
    }

    var declared =
        source.getOptions().stream().map(QuestionOption::value).collect(Collectors.toSet());
    if (!declared.containsAll(condition.values())) {
      throw ConditionRejected.value(
          "Todo valor da condição precisa ser uma opção da pergunta de origem");
    }
  }

  private static void checkNumeric(DisplayCondition condition, Question source) {
    var range = source.range().orElseThrow();

    if (condition.operator() == ConditionOperator.BETWEEN) {
      if (condition.min().orElseThrow() < range.min()
          || condition.max().orElseThrow() > range.max()) {
        throw ConditionRejected.value("A faixa da condição precisa caber na escala da origem");
      }
      return;
    }

    for (var value : condition.values()) {
      int number;
      try {
        number = Integer.parseInt(value);
      } catch (NumberFormatException notNumber) {
        throw ConditionRejected.value("Pergunta de escala só compara com números inteiros");
      }
      if (number < range.min() || number > range.max()) {
        throw ConditionRejected.value("Todo valor da condição precisa caber na escala da origem");
      }
    }
  }
}
