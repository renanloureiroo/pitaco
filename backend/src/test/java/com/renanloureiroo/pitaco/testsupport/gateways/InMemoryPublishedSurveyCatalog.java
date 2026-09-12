package com.renanloureiroo.pitaco.testsupport.gateways;

import com.renanloureiroo.pitaco.core.catalog.EventName;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryPublishedSurveyCatalog implements PublishedSurveyCatalog {

  // O que a consulta de candidatos precisa saber e o banco resolve: dono, evento e janela.
  private record Entry(
      ApplicationId applicationId,
      EventName event,
      Instant windowStart,
      Optional<Instant> windowEnd,
      boolean live,
      SurveyCandidate candidate) {}

  private final List<Entry> entries = new ArrayList<>();
  private final Map<SurveyVersionId, DeliverableSurvey> contents = new LinkedHashMap<>();
  private final Map<SurveyId, CurrentPublication> publications = new LinkedHashMap<>();

  private int candidateCalls;
  private int contentCalls;

  @Override
  public List<SurveyCandidate> candidatesFor(
      ApplicationId applicationId, EventName event, Instant now) {
    candidateCalls++;

    return entries.stream()
        .filter(entry -> entry.live())
        .filter(entry -> entry.applicationId().equals(applicationId))
        .filter(entry -> entry.event().equals(event))
        .filter(entry -> !now.isBefore(entry.windowStart()))
        .filter(entry -> entry.windowEnd().map(now::isBefore).orElse(true))
        .map(Entry::candidate)
        .toList();
  }

  @Override
  public Optional<DeliverableSurvey> contentOf(SurveyVersionId versionId) {
    contentCalls++;
    return Optional.ofNullable(contents.get(versionId));
  }

  @Override
  public Optional<PublishedVersion> publishedVersionOf(
      SurveyVersionId versionId, ApplicationId applicationId) {
    return entries.stream()
        .filter(entry -> entry.applicationId().equals(applicationId))
        .filter(entry -> entry.candidate().versionId().equals(versionId))
        .findFirst()
        .map(
            entry ->
                new PublishedVersion(
                    entry.candidate().surveyId(),
                    entry.candidate().versionId(),
                    entry.candidate().versionNumber(),
                    entry.candidate().comparabilityGroup()));
  }

  @Override
  public Optional<CurrentPublication> currentPublicationOf(SurveyId surveyId) {
    return Optional.ofNullable(publications.get(surveyId));
  }

  public InMemoryPublishedSurveyCatalog withCurrentPublication(
      SurveyId surveyId, SurveyVersionId versionId, Optional<EventName> event) {
    publications.put(surveyId, new CurrentPublication(versionId, event));
    return this;
  }

  public InMemoryPublishedSurveyCatalog withCandidate(
      ApplicationId applicationId,
      EventName event,
      Instant windowStart,
      Optional<Instant> windowEnd,
      SurveyCandidate candidate) {
    entries.add(new Entry(applicationId, event, windowStart, windowEnd, true, candidate));
    return this;
  }

  // Publicada, porém fora do ar: pausada, encerrada ou com a pesquisa em rascunho. O banco a
  // descarta na junção; aqui basta não devolvê-la como candidata.
  public InMemoryPublishedSurveyCatalog withOffAirCandidate(
      ApplicationId applicationId,
      EventName event,
      Instant windowStart,
      Optional<Instant> windowEnd,
      SurveyCandidate candidate) {
    entries.add(new Entry(applicationId, event, windowStart, windowEnd, false, candidate));
    return this;
  }

  public InMemoryPublishedSurveyCatalog withContent(DeliverableSurvey survey) {
    contents.put(survey.versionId(), survey);
    return this;
  }

  public int candidateCalls() {
    return candidateCalls;
  }

  public int contentCalls() {
    return contentCalls;
  }
}
