package com.renanloureiroo.pitaco.modules.collect.application.usecases;

import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.collect.application.outputs.RespondentDisplaySummaryOutput;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.RespondentRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SurveyDisplayRepository;
import com.renanloureiroo.pitaco.modules.collect.application.services.CollectScope;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayOutcome;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ListRespondentDisplaysUseCase
    implements UseCase<
        ListRespondentDisplaysUseCase.Input, ListRespondentDisplaysUseCase.Output> {

  private final RespondentRepository respondents;
  private final SurveyDisplayRepository displays;

  public ListRespondentDisplaysUseCase(
      RespondentRepository respondents, SurveyDisplayRepository displays) {
    this.respondents = respondents;
    this.displays = displays;
  }

  @Override
  public Output execute(Input input) {
    var applicationId = CollectScope.applicationIdOf(input.applicationId());
    var respondent =
        CollectScope.existingRespondentOf(respondents, applicationId, input.respondentId());

    var page =
        displays.findPageByRespondent(
            new SurveyDisplayRepository.ListRespondentDisplaysQuery(
                applicationId,
                respondent.id(),
                input.outcome(),
                input.openedFrom(),
                input.openedTo(),
                input.page(),
                input.size()));

    var items =
        page.items().stream()
            .map(
                summary ->
                    new RespondentDisplaySummaryOutput(
                        summary.surveyId(),
                        ListSurveyDisplaysUseCase.outputOf(summary.display())))
            .toList();

    // Sem identityValue: o identificador do respondente é opaco, o valor da identificação não é
    // (FR-030).
    log.info(
        "Exibições do respondente listadas application={} respondent={} total={}",
        applicationId.value(),
        respondent.id().value(),
        page.total());

    return new Output(
        items, input.page(), input.size(), page.total(), page.totalPages(input.size()));
  }

  public record Input(
      String applicationId,
      String respondentId,
      Optional<DisplayOutcome> outcome,
      Optional<Instant> openedFrom,
      Optional<Instant> openedTo,
      int page,
      int size) {}

  public record Output(
      List<RespondentDisplaySummaryOutput> items,
      int page,
      int size,
      long total,
      int totalPages) {}
}
