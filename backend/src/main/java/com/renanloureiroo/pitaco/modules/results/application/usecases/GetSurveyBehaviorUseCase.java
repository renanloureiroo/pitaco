package com.renanloureiroo.pitaco.modules.results.application.usecases;

import com.renanloureiroo.pitaco.core.catalog.InteractionEventType;
import com.renanloureiroo.pitaco.core.catalog.InteractionField;
import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.results.application.gateways.SurveyScopeGateway;
import com.renanloureiroo.pitaco.modules.results.application.outputs.SurveyBehaviorOutput;
import com.renanloureiroo.pitaco.modules.results.application.outputs.SurveyBehaviorOutput.ActiveTimeOutput;
import com.renanloureiroo.pitaco.modules.results.application.outputs.SurveyBehaviorOutput.DismissalsOutput;
import com.renanloureiroo.pitaco.modules.results.application.outputs.SurveyBehaviorOutput.MetricDefinitionOutput;
import com.renanloureiroo.pitaco.modules.results.application.outputs.SurveyBehaviorOutput.QuestionBehaviorOutput;
import com.renanloureiroo.pitaco.modules.results.application.outputs.SurveyBehaviorOutput.ViaCountOutput;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyBehaviorReadModel;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyBehaviorReadModel.DismissalCount;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyBehaviorReadModel.QuestionAbandonment;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyBehaviorReadModel.QuestionActiveTime;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyBehaviorReadModel.QuestionFunnel;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyResultsReadModel;
import com.renanloureiroo.pitaco.modules.results.application.readmodels.SurveyResultsReadModel.QuestionDefinition;
import com.renanloureiroo.pitaco.modules.results.application.services.ResultsScope;
import com.renanloureiroo.pitaco.modules.results.domain.behavior.BehaviorMetric;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

// Só leitura, com o mesmo recorte e o mesmo prazo de abandono dos resultados. As perguntas vêm
// da definição publicada, então pergunta sem evento nenhum aparece com zeros, e não some.
@Slf4j
public class GetSurveyBehaviorUseCase
    implements UseCase<GetSurveyBehaviorUseCase.Input, SurveyBehaviorOutput> {

  private static final List<String> VIAS =
      InteractionEventType.SURVEY_DISMISSED.field("via").map(InteractionField::choices).orElseThrow();

  private final SurveyScopeGateway surveys;
  private final SurveyResultsReadModel results;
  private final SurveyBehaviorReadModel behavior;
  private final Duration displayTimeout;

  public GetSurveyBehaviorUseCase(
      SurveyScopeGateway surveys,
      SurveyResultsReadModel results,
      SurveyBehaviorReadModel behavior,
      Duration displayTimeout) {
    this.surveys = surveys;
    this.results = results;
    this.behavior = behavior;
    this.displayTimeout = displayTimeout;
  }

  public record Input(String applicationId, String surveyId, ResultsSelection selection) {}

  @Override
  public SurveyBehaviorOutput execute(Input input) {
    var resolved = ResultsScope.resolve(surveys, input.applicationId(), input.surveyId());
    var selection = input.selection();

    if (!resolved.scope().everPublished()) {
      return new SurveyBehaviorOutput(
          false, 0, 0, List.of(), dismissalsOf(List.of()), selection.toOutput(), definitions());
    }

    var filter = selection.toFilter(resolved.applicationId(), resolved.surveyId());
    var totals = behavior.totalsOf(filter);
    var funnels = byKey(behavior.funnelsOf(filter), QuestionFunnel::key);
    var abandonments =
        byKey(
            behavior.abandonmentsOf(filter, Instant.now().minus(displayTimeout)),
            QuestionAbandonment::key);
    var activeTimes = byKey(behavior.activeTimesOf(filter), QuestionActiveTime::key);

    var questions =
        results.questionsOf(resolved.surveyId(), selection.versionNumber()).stream()
            .sorted(
                Comparator.comparingInt(QuestionDefinition::position)
                    .thenComparing(definition -> definition.key().value()))
            .map(
                definition ->
                    questionOf(
                        definition,
                        funnels.get(definition.key()),
                        abandonments.get(definition.key()),
                        activeTimes.get(definition.key())))
            .toList();

    log.info(
        "Comportamento consultado application={} survey={} instrumented={} questions={}",
        resolved.applicationId().value(),
        resolved.surveyId().value(),
        totals.instrumented(),
        questions.size());

    return new SurveyBehaviorOutput(
        true,
        totals.displayed(),
        totals.instrumented(),
        questions,
        dismissalsOf(behavior.dismissalsOf(filter)),
        selection.toOutput(),
        definitions());
  }

  private static QuestionBehaviorOutput questionOf(
      QuestionDefinition definition,
      QuestionFunnel funnel,
      QuestionAbandonment abandonment,
      QuestionActiveTime activeTime) {
    var counted =
        funnel == null ? new QuestionFunnel(definition.key(), 0, 0, 0, 0, 0, 0, 0, 0) : funnel;

    return new QuestionBehaviorOutput(
        definition.key(),
        definition.statement(),
        definition.type(),
        definition.position(),
        counted.viewed(),
        counted.answered(),
        counted.skipped(),
        abandonment == null ? 0 : abandonment.abandoned(),
        activeTime == null
            ? new ActiveTimeOutput(0, Optional.empty(), Optional.empty())
            : new ActiveTimeOutput(
                activeTime.samples(),
                Optional.of(Math.round(activeTime.medianMs())),
                Optional.of(Math.round(activeTime.p90Ms()))),
        counted.revisited(),
        BehaviorMetric.rate(counted.revisited(), counted.viewed()),
        counted.selected(),
        counted.changed(),
        BehaviorMetric.rate(counted.changed(), counted.selected()),
        counted.blockedDisplays(),
        counted.blocks());
  }

  // As seis vias do catálogo sempre presentes, com zero: via que não aparece é informação.
  private static DismissalsOutput dismissalsOf(List<DismissalCount> counts) {
    var total = counts.stream().mapToLong(DismissalCount::count).sum();
    var known =
        counts.stream()
            .filter(count -> count.via().filter(VIAS::contains).isPresent())
            .collect(Collectors.toMap(count -> count.via().get(), DismissalCount::count, Long::sum));

    var byVia =
        VIAS.stream()
            .map(
                via -> {
                  var count = known.getOrDefault(via, 0L);
                  return new ViaCountOutput(via, count, BehaviorMetric.rate(count, total));
                })
            .toList();

    var unspecified = total - known.values().stream().mapToLong(Long::longValue).sum();
    return new DismissalsOutput(total, byVia, unspecified);
  }

  private static List<MetricDefinitionOutput> definitions() {
    return Arrays.stream(BehaviorMetric.values())
        .map(metric -> new MetricDefinitionOutput(metric.key(), metric.definition()))
        .toList();
  }

  private static <T> Map<QuestionKey, T> byKey(List<T> rows, Function<T, QuestionKey> key) {
    return rows.stream().collect(Collectors.toMap(key, row -> row, (a, b) -> a));
  }
}
