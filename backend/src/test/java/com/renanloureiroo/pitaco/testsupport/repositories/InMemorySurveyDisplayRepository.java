package com.renanloureiroo.pitaco.testsupport.repositories;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.core.pagination.Page;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SurveyDisplayRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.RespondentId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.SurveyDisplay;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class InMemorySurveyDisplayRepository implements SurveyDisplayRepository {

  // O número da versão vem de junção no adaptador; aqui vem deste registro, com 1 como padrão.
  public static final int DEFAULT_VERSION_NUMBER = 1;

  private final Map<DisplayId, SurveyDisplay> displays = new LinkedHashMap<>();
  private final Map<SurveyVersionId, Integer> versionNumbers = new HashMap<>();

  private int historyCalls;

  @Override
  public Optional<SurveyDisplay> findById(DisplayId id, ApplicationId applicationId) {
    return Optional.ofNullable(displays.get(id))
        .filter(display -> display.getApplicationId().equals(applicationId))
        .map(InMemorySurveyDisplayRepository::copyOf);
  }

  @Override
  public SurveyDisplay create(SurveyDisplay display) {
    displays.put(display.id(), copyOf(display));
    return display;
  }

  @Override
  public SurveyDisplay update(SurveyDisplay display) {
    displays.put(display.id(), copyOf(display));
    return display;
  }

  // Uma varredura só, como a consulta do adaptador: é o que o teste do caso de uso verifica.
  @Override
  public List<DisplayHistoryEntry> historyOf(RespondentId respondentId, List<SurveyId> surveyIds) {
    historyCalls++;

    return displays.values().stream()
        .filter(display -> display.getRespondentId().equals(respondentId))
        .filter(display -> surveyIds.contains(display.getSurveyId()))
        .map(
            display ->
                new DisplayHistoryEntry(
                    display.getSurveyId(),
                    display.getComparabilityGroup(),
                    display.getOutcome(),
                    display.getOpenedAt()))
        .toList();
  }

  @Override
  public Page<DisplaySummary> findPage(ListDisplaysQuery query) {
    var matching =
        ordered(
            displays.values().stream()
                .filter(display -> display.getApplicationId().equals(query.applicationId()))
                .filter(display -> display.getSurveyId().equals(query.surveyId()))
                .filter(matches(query.versionId(), SurveyDisplay::getVersionId))
                .filter(matches(query.outcome(), SurveyDisplay::getOutcome))
                .filter(within(query.openedFrom(), query.openedTo())));

    return new Page<>(
        matching.stream()
            .skip(query.offset())
            .limit(query.size())
            .map(this::summaryOf)
            .toList(),
        matching.size());
  }

  @Override
  public Page<RespondentDisplaySummary> findPageByRespondent(ListRespondentDisplaysQuery query) {
    var matching =
        ordered(
            displays.values().stream()
                .filter(display -> display.getApplicationId().equals(query.applicationId()))
                .filter(display -> display.getRespondentId().equals(query.respondentId()))
                .filter(matches(query.outcome(), SurveyDisplay::getOutcome))
                .filter(within(query.openedFrom(), query.openedTo())));

    return new Page<>(
        matching.stream()
            .skip(query.offset())
            .limit(query.size())
            .map(
                display ->
                    new RespondentDisplaySummary(display.getSurveyId(), summaryOf(display)))
            .toList(),
        matching.size());
  }

  public InMemorySurveyDisplayRepository withVersionNumber(
      SurveyVersionId versionId, int number) {
    versionNumbers.put(versionId, number);
    return this;
  }

  public int historyCalls() {
    return historyCalls;
  }

  // openedAt desc com desempate por id desc: é o que torna a paginação determinística.
  private static List<SurveyDisplay> ordered(Stream<SurveyDisplay> candidates) {
    return candidates
        .sorted(
            Comparator.comparing(SurveyDisplay::getOpenedAt)
                .thenComparing(display -> display.id().value())
                .reversed())
        .toList();
  }

  private static <T> Predicate<SurveyDisplay> matches(
      Optional<T> filter, java.util.function.Function<SurveyDisplay, T> of) {
    return display -> filter.map(expected -> expected.equals(of.apply(display))).orElse(true);
  }

  // Inclusivo nos dois extremos.
  private static Predicate<SurveyDisplay> within(Optional<Instant> from, Optional<Instant> to) {
    return display ->
        from.map(start -> !display.getOpenedAt().isBefore(start)).orElse(true)
            && to.map(end -> !display.getOpenedAt().isAfter(end)).orElse(true);
  }

  private DisplaySummary summaryOf(SurveyDisplay display) {
    return new DisplaySummary(
        display.id(),
        display.getVersionId(),
        versionNumbers.getOrDefault(display.getVersionId(), DEFAULT_VERSION_NUMBER),
        display.getComparabilityGroup(),
        display.getOutcome(),
        display.sdkVersion(),
        display.getOpenedAt(),
        display.closedAt());
  }

  public List<SurveyDisplay> findAll() {
    return List.copyOf(displays.values());
  }

  public boolean isEmpty() {
    return displays.isEmpty();
  }

  private static SurveyDisplay copyOf(SurveyDisplay display) {
    return SurveyDisplay.restore(
        display.id(),
        display.getApplicationId(),
        display.getRespondentId(),
        display.getSurveyId(),
        display.getVersionId(),
        display.getComparabilityGroup(),
        display.getOutcome(),
        display.sdkVersion(),
        display.getAttributes(),
        display.getOpenedAt(),
        display.closedAt());
  }
}
