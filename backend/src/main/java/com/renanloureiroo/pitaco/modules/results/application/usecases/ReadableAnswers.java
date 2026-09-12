package com.renanloureiroo.pitaco.modules.results.application.usecases;

import com.renanloureiroo.pitaco.core.catalog.QuestionOption;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyResultsReadModel.AnswerRow;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyResultsReadModel.QuestionDefinition;
import java.util.Optional;
import java.util.stream.Collectors;

// Valor de resposta em texto legível para o contexto das respostas abertas: opção pelo rótulo
// da definição atual, número como está, texto como está.
final class ReadableAnswers {

  private static final String ANSWERED = "ANSWERED";

  private ReadableAnswers() {}

  static boolean isAnswered(AnswerRow row) {
    return ANSWERED.equals(row.status());
  }

  static Optional<String> readable(AnswerRow row, Optional<QuestionDefinition> definition) {
    if (row.text().isPresent()) {
      return row.text();
    }
    if (row.number().isPresent()) {
      return row.number().map(String::valueOf);
    }
    if (row.options().isEmpty()) {
      return Optional.empty();
    }

    var labels =
        definition
            .map(
                question ->
                    question.options().stream()
                        .collect(
                            Collectors.toMap(
                                QuestionOption::value, QuestionOption::label, (a, b) -> a)))
            .orElseGet(java.util.Map::of);

    return Optional.of(
        row.options().stream()
            .map(value -> labels.getOrDefault(value, value))
            .collect(Collectors.joining(", ")));
  }
}
