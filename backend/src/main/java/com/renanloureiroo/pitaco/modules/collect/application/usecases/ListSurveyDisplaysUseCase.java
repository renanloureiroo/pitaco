package com.renanloureiroo.pitaco.modules.collect.application.usecases;

import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.SurveyScopeGateway;
import com.renanloureiroo.pitaco.modules.collect.application.outputs.DisplaySummaryOutput;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SurveyDisplayRepository;
import com.renanloureiroo.pitaco.modules.collect.application.services.CollectScope;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayOutcome;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ListSurveyDisplaysUseCase
    implements UseCase<ListSurveyDisplaysUseCase.Input, ListSurveyDisplaysUseCase.Output> {

  private final SurveyScopeGateway surveys;
  private final SurveyDisplayRepository displays;

  public ListSurveyDisplaysUseCase(SurveyScopeGateway surveys, SurveyDisplayRepository displays) {
    this.surveys = surveys;
    this.displays = displays;
  }

  @Override
  public Output execute(Input input) {
    // A existência é consultada antes: pesquisa que nunca foi exibida devolve página vazia, e
    // pesquisa inexistente devolve 404 — a distinção que a porta de escopo existe para fazer.
    var applicationId = CollectScope.applicationIdOf(input.applicationId());
    var surveyId = CollectScope.existingSurveyIdOf(surveys, applicationId, input.surveyId());

    var page =
        displays.findPage(
            new SurveyDisplayRepository.ListDisplaysQuery(
                applicationId,
                surveyId,
                input.versionNumber(),
                input.outcome(),
                input.openedFrom(),
                input.openedTo(),
                input.page(),
                input.size()));

    var items = page.items().stream().map(ListSurveyDisplaysUseCase::outputOf).toList();

    log.info(
        "Exibições listadas application={} survey={} total={}",
        applicationId.value(),
        surveyId.value(),
        page.total());

    return new Output(
        items, input.page(), input.size(), page.total(), page.totalPages(input.size()));
  }

  static DisplaySummaryOutput outputOf(SurveyDisplayRepository.DisplaySummary summary) {
    return new DisplaySummaryOutput(
        summary.id(),
        summary.versionId(),
        summary.versionNumber(),
        summary.comparabilityGroup(),
        summary.outcome(),
        summary.sdkVersion(),
        summary.openedAt(),
        summary.closedAt());
  }

  public record Input(
      String applicationId,
      String surveyId,
      Optional<Integer> versionNumber,
      Optional<DisplayOutcome> outcome,
      Optional<Instant> openedFrom,
      Optional<Instant> openedTo,
      int page,
      int size) {}

  public record Output(
      List<DisplaySummaryOutput> items, int page, int size, long total, int totalPages) {}
}
