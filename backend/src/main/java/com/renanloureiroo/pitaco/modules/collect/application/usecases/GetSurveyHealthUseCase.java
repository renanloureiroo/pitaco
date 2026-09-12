package com.renanloureiroo.pitaco.modules.collect.application.usecases;

import com.renanloureiroo.pitaco.core.catalog.EventName;
import com.renanloureiroo.pitaco.core.catalog.SdkVersion;
import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog.CurrentPublication;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.SurveyScopeGateway;
import com.renanloureiroo.pitaco.modules.collect.application.outputs.SurveyHealthOutput;
import com.renanloureiroo.pitaco.modules.collect.application.outputs.SurveyHealthOutput.ReasonCount;
import com.renanloureiroo.pitaco.modules.collect.application.outputs.SurveyHealthOutput.VersionCount;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.ObservedEventRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SuppressionEventRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SurveyDisplayRepository;
import com.renanloureiroo.pitaco.modules.collect.application.services.CollectScope;
import com.renanloureiroo.pitaco.modules.collect.application.services.RequiredSdk;
import com.renanloureiroo.pitaco.modules.collect.domain.health.SuppressionReason;
import com.renanloureiroo.pitaco.modules.collect.domain.health.SuppressionShare;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

// Duas perguntas com a mesma resposta aparente — zero respostas — e correções opostas: a
// pesquisa está sendo suprimida, ou o evento nunca chegou. Esta leitura existe para separá-las.
@Slf4j
public class GetSurveyHealthUseCase
    implements UseCase<GetSurveyHealthUseCase.Input, SurveyHealthOutput> {

  private final SurveyScopeGateway surveys;
  private final PublishedSurveyCatalog catalog;
  private final SuppressionEventRepository suppressions;
  private final SurveyDisplayRepository displays;
  private final ObservedEventRepository events;
  private final Duration defaultWindow;
  private final double relevantShare;
  private final long relevantMinimum;

  public GetSurveyHealthUseCase(
      SurveyScopeGateway surveys,
      PublishedSurveyCatalog catalog,
      SuppressionEventRepository suppressions,
      SurveyDisplayRepository displays,
      ObservedEventRepository events,
      Duration defaultWindow,
      double relevantShare,
      long relevantMinimum) {
    this.surveys = surveys;
    this.catalog = catalog;
    this.suppressions = suppressions;
    this.displays = displays;
    this.events = events;
    this.defaultWindow = defaultWindow;
    this.relevantShare = relevantShare;
    this.relevantMinimum = relevantMinimum;
  }

  public record Input(
      String applicationId, String surveyId, Optional<Instant> from, Optional<Instant> to) {}

  @Override
  public SurveyHealthOutput execute(Input input) {
    var applicationId = CollectScope.applicationIdOf(input.applicationId());
    var surveyId = CollectScope.existingSurveyIdOf(surveys, applicationId, input.surveyId());

    var now = Instant.now();
    var from = input.from().orElseGet(() -> input.to().orElse(now).minus(defaultWindow));
    var to = input.to().orElseGet(() -> from.isAfter(now) ? from : now);

    var counts = suppressions.countFor(surveyId, from, to);
    var displayed = displays.countOpenedBetween(surveyId, from, to);
    var share = new SuppressionShare(displayed, counts.total());

    var publication = catalog.currentPublicationOf(surveyId);
    var minRequired =
        publication
            .flatMap(current -> catalog.contentOf(current.versionId()))
            .map(RequiredSdk::of)
            .map(SdkVersion::value);
    var event = publication.flatMap(CurrentPublication::event);
    var eventLastSeenAt = event.flatMap(name -> events.lastSeenAt(applicationId, name));

    var output =
        new SurveyHealthOutput(
            from,
            to,
            displayed,
            counts.total(),
            Arrays.stream(SuppressionReason.values())
                .map(
                    reason ->
                        new ReasonCount(
                            reason.name().toLowerCase(Locale.ROOT),
                            counts.byReason().getOrDefault(reason, 0L)))
                .toList(),
            counts.bySdkVersion().stream()
                .map(version -> new VersionCount(version.sdkVersion(), version.count()))
                .toList(),
            share.share(),
            share.isRelevant(relevantShare, relevantMinimum),
            minRequired,
            event.map(EventName::value),
            eventLastSeenAt);

    log.info(
        "Saúde da pesquisa consultada survey={} exibicoes={} supressoes={} relevante={}",
        surveyId.value(),
        displayed,
        counts.total(),
        output.relevant());

    return output;
  }
}
