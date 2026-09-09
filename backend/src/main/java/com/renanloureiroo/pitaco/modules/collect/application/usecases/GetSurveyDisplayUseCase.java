package com.renanloureiroo.pitaco.modules.collect.application.usecases;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.collect.application.errors.DisplayNotFound;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.ApplicationScopeGateway;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog;
import com.renanloureiroo.pitaco.modules.collect.application.outputs.AnswerReadOutput;
import com.renanloureiroo.pitaco.modules.collect.application.outputs.AnswerReadStatus;
import com.renanloureiroo.pitaco.modules.collect.application.outputs.DisplayDetailOutput;
import com.renanloureiroo.pitaco.modules.collect.application.outputs.DisplaySummaryOutput;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.AnswerRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SurveyDisplayRepository;
import com.renanloureiroo.pitaco.modules.collect.application.services.CollectScope;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.Answer;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.AnswerStatus;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.SurveyDisplay;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.AnswerValue;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GetSurveyDisplayUseCase
    implements UseCase<GetSurveyDisplayUseCase.Input, DisplayDetailOutput> {

  // Depois de todas as que a versão conhece, com desempate estável pela chave: é o que a spec
  // manda fazer com a chave que a versão exibida não tem.
  private static final int UNKNOWN_POSITION = Integer.MAX_VALUE;

  private final ApplicationScopeGateway applications;
  private final PublishedSurveyCatalog catalog;
  private final SurveyDisplayRepository displays;
  private final AnswerRepository answers;

  public GetSurveyDisplayUseCase(
      ApplicationScopeGateway applications,
      PublishedSurveyCatalog catalog,
      SurveyDisplayRepository displays,
      AnswerRepository answers) {
    this.applications = applications;
    this.catalog = catalog;
    this.displays = displays;
    this.answers = answers;
  }

  @Override
  public DisplayDetailOutput execute(Input input) {
    var applicationId = CollectScope.applicationIdOf(input.applicationId());

    DisplayId displayId;
    try {
      displayId = DisplayId.of(input.displayId());
    } catch (DomainException malformed) {
      throw new DisplayNotFound();
    }

    var display = displays.findById(displayId, applicationId).orElseThrow(DisplayNotFound::new);

    // A versão de uma exibição é sempre publicada, e publicação não se desfaz; conteúdo ausente
    // é a exibição que não há como apresentar, e não uma exibição parcial.
    var content =
        catalog.contentOf(display.getVersionId()).orElseThrow(DisplayNotFound::new);
    var retentionDays = applications.effectiveOpenTextRetentionDaysOf(applicationId);
    var now = Instant.now();

    var positions = positionsOf(content);
    var read =
        answers.findByDisplay(display.id()).stream()
            .map(answer -> outputOf(answer, retentionDays, now))
            .sorted(
                Comparator.comparingInt(
                        (AnswerReadOutput answer) ->
                            positions.getOrDefault(answer.questionKey(), UNKNOWN_POSITION))
                    .thenComparing(answer -> answer.questionKey().value()))
            .toList();

    // Sem conteúdo de resposta e sem valor de atributo: o log diz o que foi lido, nunca o que
    // a pessoa respondeu (FR-030).
    log.info(
        "Exibição consultada application={} display={} answers={}",
        applicationId.value(),
        display.id().value(),
        read.size());

    return new DisplayDetailOutput(
        summaryOf(display, content.versionNumber()),
        display.getRespondentId(),
        display.getSurveyId(),
        display.getAttributes().values(),
        read);
  }

  private static Map<QuestionKey, Integer> positionsOf(
      PublishedSurveyCatalog.DeliverableSurvey content) {
    return content.questions().stream()
        .collect(
            java.util.stream.Collectors.toMap(
                PublishedSurveyCatalog.DeliverableQuestion::key,
                PublishedSurveyCatalog.DeliverableQuestion::position,
                (first, second) -> first));
  }

  private static DisplaySummaryOutput summaryOf(SurveyDisplay display, int versionNumber) {
    return new DisplaySummaryOutput(
        display.id(),
        display.getVersionId(),
        versionNumber,
        display.getComparabilityGroup(),
        display.getOutcome(),
        display.sdkVersion(),
        display.getOpenedAt(),
        display.closedAt());
  }

  // A supressão atinge só texto livre, e só quando há prazo configurado: prazo ausente significa
  // sem expiração, não expiração imediata (D-07).
  private static AnswerReadOutput outputOf(
      Answer answer, Optional<Integer> retentionDays, Instant now) {
    if (answer.getStatus() == AnswerStatus.SKIPPED) {
      return AnswerReadOutput.withoutValue(answer.getQuestionKey(), AnswerReadStatus.SKIPPED);
    }

    var value = answer.value().orElseThrow();

    return switch (value) {
      case AnswerValue.NumericValue number ->
          new AnswerReadOutput(
              answer.getQuestionKey(),
              AnswerReadStatus.ANSWERED,
              Optional.empty(),
              Optional.of(number.value()),
              List.of());
      case AnswerValue.ChoiceValue choice ->
          new AnswerReadOutput(
              answer.getQuestionKey(),
              AnswerReadStatus.ANSWERED,
              Optional.empty(),
              Optional.empty(),
              choice.options());
      case AnswerValue.TextValue text ->
          expired(answer, retentionDays, now)
              ? AnswerReadOutput.withoutValue(answer.getQuestionKey(), AnswerReadStatus.EXPIRED)
              : new AnswerReadOutput(
                  answer.getQuestionKey(),
                  AnswerReadStatus.ANSWERED,
                  Optional.of(text.text().value()),
                  Optional.empty(),
                  List.of());
    };
  }

  private static boolean expired(Answer answer, Optional<Integer> retentionDays, Instant now) {
    return retentionDays
        .map(days -> answer.getAnsweredAt().plus(Duration.ofDays(days)).isBefore(now))
        .orElse(false);
  }

  public record Input(String applicationId, String displayId) {}
}
