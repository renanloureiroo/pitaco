package com.renanloureiroo.pitaco.modules.results.application.usecases;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.results.application.gateways.SurveyScopeGateway;
import com.renanloureiroo.pitaco.modules.results.application.outputs.OpenAnswerOutput;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyResultsReadModel;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyResultsReadModel.AnswerRow;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyResultsReadModel.OpenAnswerRow;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyResultsReadModel.OpenAnswersQuery;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyResultsReadModel.QuestionDefinition;
import com.renanloureiroo.pitaco.modules.results.application.services.ResultsScope;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ListOpenAnswersUseCase
    implements UseCase<ListOpenAnswersUseCase.Input, ListOpenAnswersUseCase.Output> {

  private final SurveyScopeGateway surveys;
  private final SurveyResultsReadModel results;

  public ListOpenAnswersUseCase(SurveyScopeGateway surveys, SurveyResultsReadModel results) {
    this.surveys = surveys;
    this.results = results;
  }

  public record Input(
      String applicationId,
      String surveyId,
      ResultsSelection selection,
      Optional<String> term,
      int page,
      int size) {}

  public record Output(
      List<OpenAnswerOutput> items, int page, int size, long total, int totalPages) {}

  @Override
  public Output execute(Input input) {
    var resolved = ResultsScope.resolve(surveys, input.applicationId(), input.surveyId());
    var filter = input.selection().toFilter(resolved.applicationId(), resolved.surveyId());

    // Texto vencido pela retenção nem chega da consulta: ausente do prazo significa sem
    // expiração, não expiração imediata.
    var now = Instant.now();
    var notBefore = resolved.scope().openTextRetentionDays().map(days -> now.minus(Duration.ofDays(days)));

    var page =
        results.openAnswersOf(
            new OpenAnswersQuery(
                filter,
                input.term().map(String::strip).filter(term -> !term.isEmpty()),
                notBefore,
                input.page(),
                input.size()));

    var definitions =
        GetSurveyResultsUseCase.byKey(results.questionsOf(resolved.surveyId(), Optional.empty()));
    var context = contextOf(page.items(), notBefore);

    var items =
        page.items().stream()
            .map(row -> outputOf(row, definitions, context.getOrDefault(row.displayId(), List.of())))
            .toList();

    log.info(
        "Respostas abertas listadas application={} survey={} total={}",
        resolved.applicationId().value(),
        resolved.surveyId().value(),
        page.total());

    return new Output(items, input.page(), input.size(), page.total(), page.totalPages(input.size()));
  }

  // Uma consulta para o contexto de toda a página, nunca uma por resposta.
  private Map<String, List<AnswerRow>> contextOf(
      List<OpenAnswerRow> rows, Optional<Instant> notBefore) {
    var displayIds = rows.stream().map(OpenAnswerRow::displayId).distinct().toList();
    if (displayIds.isEmpty()) {
      return Map.of();
    }

    return results.answersOf(displayIds).stream()
        .filter(ReadableAnswers::isAnswered)
        .filter(answer -> answer.text().isEmpty() || !expired(answer, notBefore))
        .collect(Collectors.groupingBy(AnswerRow::displayId));
  }

  private static boolean expired(AnswerRow answer, Optional<Instant> notBefore) {
    return notBefore.map(cutoff -> answer.answeredAt().isBefore(cutoff)).orElse(false);
  }

  private static OpenAnswerOutput outputOf(
      OpenAnswerRow row, Map<QuestionKey, QuestionDefinition> definitions, List<AnswerRow> context) {
    return new OpenAnswerOutput(
        row.displayId(),
        row.key(),
        statementOf(row.key(), definitions),
        row.text(),
        row.answeredAt(),
        context.stream()
            .filter(answer -> !answer.key().equals(row.key()))
            .flatMap(
                answer ->
                    ReadableAnswers.readable(
                            answer, Optional.ofNullable(definitions.get(answer.key())))
                        .map(
                            value ->
                                new OpenAnswerOutput.AnswerContext(
                                    answer.key(), statementOf(answer.key(), definitions), value))
                        .stream())
            .toList());
  }

  private static String statementOf(QuestionKey key, Map<QuestionKey, QuestionDefinition> definitions) {
    var definition = definitions.get(key);
    return definition == null ? key.value() : definition.statement();
  }
}
