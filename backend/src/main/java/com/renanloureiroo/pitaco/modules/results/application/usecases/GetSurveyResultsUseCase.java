package com.renanloureiroo.pitaco.modules.results.application.usecases;

import com.renanloureiroo.pitaco.modules.results.application.outputs.RetentionOutput;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyResultsReadModel.RetainedSummary;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyResultsReadModel.RetainedCount;
import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.results.application.gateways.SurveyScopeGateway;
import com.renanloureiroo.pitaco.modules.results.application.gateways.SurveyScopeGateway.SurveyScope;
import com.renanloureiroo.pitaco.modules.results.application.outputs.AttributeCatalogOutput;
import com.renanloureiroo.pitaco.modules.results.application.outputs.NpsSummaryOutput;
import com.renanloureiroo.pitaco.modules.results.domain.aggregation.QuestionAggregate;
import com.renanloureiroo.pitaco.modules.results.domain.comparability.QuestionComparability;
import com.renanloureiroo.pitaco.modules.results.domain.comparability.QuestionShape;
import java.util.Optional;
import com.renanloureiroo.pitaco.modules.results.application.outputs.QuestionResultOutput;
import com.renanloureiroo.pitaco.modules.results.application.outputs.ResponseRateOutput;
import com.renanloureiroo.pitaco.modules.results.application.outputs.SurveyResultsOutput;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyResultsReadModel;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyResultsReadModel.AnswerCounts;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyResultsReadModel.AttributeValueCount;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyResultsReadModel.DisplayCounts;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyResultsReadModel.QuestionDefinition;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyResultsReadModel.ResultsFilter;
import com.renanloureiroo.pitaco.modules.results.application.services.ResultsScope;
import com.renanloureiroo.pitaco.modules.results.domain.ResponseRate;
import com.renanloureiroo.pitaco.modules.results.domain.aggregation.QuestionAggregation;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

// Só leitura, e cada número sai de uma consulta agrupada: nada é somado em memória linha a
// linha, e nada passa pelo domínio da coleta.
@Slf4j
public class GetSurveyResultsUseCase
    implements UseCase<GetSurveyResultsUseCase.Input, SurveyResultsOutput> {

  private final SurveyScopeGateway surveys;
  private final SurveyResultsReadModel results;
  private final Duration displayTimeout;
  private final int smallSampleThreshold;

  public GetSurveyResultsUseCase(
      SurveyScopeGateway surveys,
      SurveyResultsReadModel results,
      Duration displayTimeout,
      int smallSampleThreshold) {
    this.surveys = surveys;
    this.results = results;
    this.displayTimeout = displayTimeout;
    this.smallSampleThreshold = smallSampleThreshold;
  }

  public record Input(String applicationId, String surveyId, ResultsSelection selection) {}

  @Override
  public SurveyResultsOutput execute(Input input) {
    var resolved = ResultsScope.resolve(surveys, input.applicationId(), input.surveyId());
    var filter = input.selection().toFilter(resolved.applicationId(), resolved.surveyId());

    // Rascunho que nunca publicou não tem exibição: o estado vazio sai sem ir ao banco.
    if (!resolved.scope().everPublished()) {
      return empty(input.selection());
    }

    var abandonedBefore = Instant.now().minus(displayTimeout);
    var counts = results.displayCountsOf(filter, abandonedBefore);

    // O congelado pela retenção só soma quando o recorte é só de versão: ele não sabe dia nem
    // atributo, e somá-lo a um recorte de período contaria o que está fora dele.
    var versionNumber = input.selection().versionNumber();
    var retained = results.retainedSummaryOf(resolved.surveyId(), versionNumber);
    var summable = input.selection().isVersionOnly();
    var retainedCounts =
        retained.isPresent() && summable
            ? results.retainedCountsOf(resolved.surveyId(), versionNumber)
            : List.<RetainedCount>of();
    var retention =
        retained.map(
            summary ->
                summable
                    ? RetentionOutput.applied(summary.discardedBefore())
                    : RetentionOutput.notApplied(summary.discardedBefore()));
    var responding =
        counts.responding()
            + retained.filter(summary -> summable).map(RetainedSummary::respondingDisplays).orElse(0L);

    var questions =
        withComparability(
            questionsOf(filter, input.selection(), retainedCounts), filter, input.selection());

    log.info(
        "Resultados consultados application={} survey={} displayed={} questions={}",
        resolved.applicationId().value(),
        resolved.surveyId().value(),
        counts.displayed(),
        questions.size());

    return new SurveyResultsOutput(
        true,
        responseRateOf(counts, filter),
        questions,
        responding,
        responding < smallSampleThreshold,
        input.selection().toOutput(),
        catalogOf(results.attributeCatalogOf(resolved.applicationId(), resolved.surveyId())),
        npsOf(resolved.scope(), questions),
        retention);
  }

  // Só no consolidado. A regra da comparabilidade é do domínio; aqui entram as duas leituras de
  // que ela precisa, uma consulta cada, para a pesquisa inteira.
  private List<QuestionResultOutput> withComparability(
      List<QuestionResultOutput> questions, ResultsFilter filter, ResultsSelection selection) {
    if (selection.versionNumber().isPresent() || questions.isEmpty()) {
      return questions;
    }

    var shapes =
        results.questionShapesOf(filter.surveyId()).stream()
            .collect(Collectors.groupingBy(QuestionShape::key));
    var displayed = results.displayedVersionsOf(filter);

    return questions.stream()
        .map(
            question ->
                question.withComparability(
                    QuestionComparability.of(
                        shapes.getOrDefault(question.key(), List.of()), displayed)))
        .toList();
  }

  // Pesquisa nascida do modelo de NPS traz o número no topo: é a pergunta que ela existe para
  // responder, e ninguém deveria precisar achá-la no meio das outras nem fazer a conta.
  private static Optional<NpsSummaryOutput> npsOf(
      SurveyScope scope, List<QuestionResultOutput> questions) {
    if (!scope.isNpsTemplate()) {
      return Optional.empty();
    }

    return questions.stream()
        .filter(question -> question.type() == QuestionType.NPS)
        .findFirst()
        .map(GetSurveyResultsUseCase::summaryOf);
  }

  private static NpsSummaryOutput summaryOf(QuestionResultOutput question) {
    return question
        .aggregate()
        .filter(QuestionAggregate.Nps.class::isInstance)
        .map(QuestionAggregate.Nps.class::cast)
        .map(
            nps ->
                new NpsSummaryOutput(
                    question.key(),
                    question.answered(),
                    nps.promoters(),
                    nps.passives(),
                    nps.detractors(),
                    Optional.of(nps.score())))
        .orElseGet(
            () -> new NpsSummaryOutput(question.key(), 0, 0, 0, 0, Optional.empty()));
  }

  private SurveyResultsOutput empty(ResultsSelection selection) {
    return new SurveyResultsOutput(
        false,
        new ResponseRateOutput(0, 0, 0, 0, 0, java.util.Optional.empty(), ResponseRate.DEFINITION, List.of()),
        List.of(),
        0,
        true,
        selection.toOutput(),
        List.of());
  }

  private ResponseRateOutput responseRateOf(DisplayCounts counts, ResultsFilter filter) {
    var rate =
        ResponseRate.of(
            counts.displayed(),
            counts.completed(),
            counts.dismissed(),
            counts.abandoned(),
            counts.inProgress());

    return new ResponseRateOutput(
        rate.displayed(),
        rate.completed(),
        rate.dismissed(),
        rate.abandoned(),
        rate.inProgress(),
        rate.rate(),
        ResponseRate.DEFINITION,
        results.timelineOf(filter).stream()
            .map(
                day -> new ResponseRateOutput.TimelinePoint(day.day(), day.displayed(), day.completed()))
            .toList());
  }

  private List<QuestionResultOutput> questionsOf(
      ResultsFilter filter, ResultsSelection selection, List<RetainedCount> retained) {
    var definitions = results.questionsOf(filter.surveyId(), selection.versionNumber());
    if (definitions.isEmpty()) {
      return List.of();
    }

    var answerCounts = new LinkedHashMap<QuestionKey, AnswerCounts>();
    results.answerCountsOf(filter).forEach(count -> answerCounts.put(count.key(), count));
    var optionCounts = new LinkedHashMap<QuestionKey, Map<String, Long>>();
    results
        .optionCountsOf(filter)
        .forEach(
            count ->
                optionCounts
                    .computeIfAbsent(count.key(), key -> new LinkedHashMap<>())
                    .merge(count.value(), count.count(), Long::sum));
    var numericCounts = new LinkedHashMap<QuestionKey, Map<Integer, Long>>();
    results
        .numericCountsOf(filter)
        .forEach(
            count ->
                numericCounts
                    .computeIfAbsent(count.key(), key -> new LinkedHashMap<>())
                    .merge(count.value(), count.count(), Long::sum));
    retained.forEach(count -> addRetained(count, answerCounts, optionCounts, numericCounts));

    return definitions.stream()
        .sorted(
            Comparator.comparingInt(QuestionDefinition::position)
                .thenComparing(definition -> definition.key().value()))
        .map(
            definition ->
                resultOf(
                    definition,
                    answerCounts.get(definition.key()),
                    optionCounts.getOrDefault(definition.key(), Map.<String, Long>of()),
                    numericCounts.getOrDefault(definition.key(), Map.<Integer, Long>of())))
        .toList();
  }

  private static void addRetained(
      RetainedCount retained,
      Map<QuestionKey, AnswerCounts> answerCounts,
      Map<QuestionKey, Map<String, Long>> optionCounts,
      Map<QuestionKey, Map<Integer, Long>> numericCounts) {
    var key = retained.key();

    switch (retained.dimension()) {
      case "STATUS" -> {
        var frozen =
            new AnswerCounts(
                key,
                "ANSWERED".equals(retained.value()) ? retained.count() : 0,
                "SKIPPED".equals(retained.value()) ? retained.count() : 0,
                "NOT_APPLICABLE".equals(retained.value()) ? retained.count() : 0);
        answerCounts.merge(
            key,
            frozen,
            (live, extra) ->
                new AnswerCounts(
                    key,
                    live.answered() + extra.answered(),
                    live.skipped() + extra.skipped(),
                    live.notApplicable() + extra.notApplicable()));
      }
      case "OPTION" ->
          optionCounts
              .computeIfAbsent(key, ignored -> new LinkedHashMap<>())
              .merge(retained.value(), retained.count(), Long::sum);
      case "NUMBER" ->
          numericCounts
              .computeIfAbsent(key, ignored -> new LinkedHashMap<>())
              .merge(Integer.parseInt(retained.value()), retained.count(), Long::sum);
      default -> {}
    }
  }

  private static QuestionResultOutput resultOf(
      QuestionDefinition definition,
      AnswerCounts counts,
      Map<String, Long> optionCounts,
      Map<Integer, Long> numericCounts) {
    var answered = counts == null ? 0 : counts.answered();
    var skipped = counts == null ? 0 : counts.skipped();

    return new QuestionResultOutput(
        definition.key(),
        definition.statement(),
        definition.type(),
        definition.position(),
        answered,
        skipped,
        counts == null ? 0 : counts.notApplicable(),
        QuestionAggregation.aggregate(
            definition.type(),
            definition.options(),
            definition.range(),
            answered,
            optionCounts,
            numericCounts));
  }

  private static List<AttributeCatalogOutput> catalogOf(List<AttributeValueCount> catalog) {
    var byName = new LinkedHashMap<String, List<AttributeCatalogOutput.ValueCount>>();
    catalog.stream()
        .sorted(
            Comparator.comparing(AttributeValueCount::name)
                .thenComparing(AttributeValueCount::value))
        .forEach(
            entry ->
                byName
                    .computeIfAbsent(entry.name(), name -> new java.util.ArrayList<>())
                    .add(new AttributeCatalogOutput.ValueCount(entry.value(), entry.count())));

    return byName.entrySet().stream()
        .map(entry -> new AttributeCatalogOutput(entry.getKey(), List.copyOf(entry.getValue())))
        .toList();
  }

  static Map<QuestionKey, QuestionDefinition> byKey(List<QuestionDefinition> definitions) {
    return definitions.stream()
        .collect(Collectors.toMap(QuestionDefinition::key, definition -> definition, (a, b) -> a));
  }
}
