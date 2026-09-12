package com.renanloureiroo.pitaco.modules.results.application.usecases;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.results.application.gateways.SurveyScopeGateway;
import com.renanloureiroo.pitaco.modules.results.application.outputs.ExportOutput;
import com.renanloureiroo.pitaco.modules.results.application.outputs.ExportOutput.ExportAnswer;
import com.renanloureiroo.pitaco.modules.results.application.outputs.ExportOutput.ExportQuestion;
import com.renanloureiroo.pitaco.modules.results.application.outputs.ExportOutput.ExportRow;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyResultsReadModel;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyResultsReadModel.AnswerRow;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyResultsReadModel.AttributeRow;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyResultsReadModel.DisplayCursor;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyResultsReadModel.DisplayRow;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyResultsReadModel.QuestionDefinition;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyResultsReadModel.ResultsFilter;
import com.renanloureiroo.pitaco.modules.results.application.services.ResultsScope;
import com.renanloureiroo.pitaco.modules.results.domain.DisplayResolution;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

// Escopo e cabeçalho resolvidos aqui, síncronos; as linhas saem em lotes por cursor quando a
// borda as consome. Uma linha por exibição, dispensadas e abandonadas inclusive.
@Slf4j
public class ExportSurveyResultsUseCase
    implements UseCase<ExportSurveyResultsUseCase.Input, ExportOutput> {

  static final int BATCH_SIZE = 500;

  private static final String NOT_APPLICABLE = "NOT_APPLICABLE";

  private final SurveyScopeGateway surveys;
  private final SurveyResultsReadModel results;
  private final Duration displayTimeout;

  public ExportSurveyResultsUseCase(
      SurveyScopeGateway surveys, SurveyResultsReadModel results, Duration displayTimeout) {
    this.surveys = surveys;
    this.results = results;
    this.displayTimeout = displayTimeout;
  }

  public record Input(String applicationId, String surveyId, ResultsSelection selection) {}

  @Override
  public ExportOutput execute(Input input) {
    var resolved = ResultsScope.resolve(surveys, input.applicationId(), input.surveyId());
    var filter = input.selection().toFilter(resolved.applicationId(), resolved.surveyId());

    var now = Instant.now();
    var abandonedBefore = now.minus(displayTimeout);
    var notBefore =
        resolved.scope().openTextRetentionDays().map(days -> now.minus(Duration.ofDays(days)));

    var questions =
        results.questionsOf(resolved.surveyId(), input.selection().versionNumber()).stream()
            .sorted(
                Comparator.comparingInt(QuestionDefinition::position)
                    .thenComparing(definition -> definition.key().value()))
            .map(definition -> new ExportQuestion(definition.key(), definition.statement()))
            .toList();
    var attributeNames = results.attributeNamesOf(filter);

    log.info(
        "Exportação preparada application={} survey={} questions={} attributes={}",
        resolved.applicationId().value(),
        resolved.surveyId().value(),
        questions.size(),
        attributeNames.size());

    return new ExportOutput(
        attributeNames,
        questions,
        consumer -> stream(filter, abandonedBefore, notBefore, consumer),
        resolved.scope().mayContainPersonalData());
  }

  private void stream(
      ResultsFilter filter,
      Instant abandonedBefore,
      Optional<Instant> notBefore,
      Consumer<ExportRow> consumer) {
    Optional<DisplayCursor> cursor = Optional.empty();

    while (true) {
      var batch = results.displaysAfter(filter, cursor, BATCH_SIZE);
      if (batch.isEmpty()) {
        return;
      }

      var ids = batch.stream().map(DisplayRow::id).toList();
      var attributes =
          results.attributesOf(ids).stream().collect(Collectors.groupingBy(AttributeRow::displayId));
      var answers =
          results.answersOf(ids).stream().collect(Collectors.groupingBy(AnswerRow::displayId));

      for (var display : batch) {
        consumer.accept(
            rowOf(
                display,
                abandonedBefore,
                notBefore,
                attributes.getOrDefault(display.id(), List.of()),
                answers.getOrDefault(display.id(), List.of())));
      }

      if (batch.size() < BATCH_SIZE) {
        return;
      }
      var last = batch.get(batch.size() - 1);
      cursor = Optional.of(new DisplayCursor(last.openedAt(), last.id()));
    }
  }

  private static ExportRow rowOf(
      DisplayRow display,
      Instant abandonedBefore,
      Optional<Instant> notBefore,
      List<AttributeRow> attributes,
      List<AnswerRow> answers) {
    var attributeValues = new LinkedHashMap<String, String>();
    attributes.forEach(attribute -> attributeValues.put(attribute.name(), attribute.value()));

    var answerValues = new LinkedHashMap<QuestionKey, ExportAnswer>();
    for (var answer : answers) {
      if (ReadableAnswers.isAnswered(answer)) {
        answerValues.put(answer.key(), answerOf(answer, notBefore));
      } else if (NOT_APPLICABLE.equals(answer.status())) {
        answerValues.put(answer.key(), ExportAnswer.NOT_APPLICABLE);
      }
    }

    return new ExportRow(
        display.id(),
        display.respondentReference(),
        display.versionNumber(),
        DisplayResolution.of(display.storedOutcome(), display.openedAt(), abandonedBefore),
        display.openedAt(),
        display.closedAt(),
        display.sdkVersion(),
        Map.copyOf(attributeValues),
        Map.copyOf(answerValues));
  }

  private static ExportAnswer answerOf(AnswerRow answer, Optional<Instant> notBefore) {
    var expired = notBefore.map(cutoff -> answer.answeredAt().isBefore(cutoff)).orElse(false);
    if (answer.text().isPresent() && expired) {
      return ExportAnswer.EMPTY;
    }
    return new ExportAnswer(answer.text(), answer.number(), answer.options());
  }
}
