package com.renanloureiroo.pitaco.testsupport.repositories;

import com.renanloureiroo.pitaco.core.catalog.SdkVersion;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SuppressionEventRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.health.SuppressionDedupKey;
import com.renanloureiroo.pitaco.modules.collect.domain.health.SuppressionEvent;
import com.renanloureiroo.pitaco.modules.collect.domain.health.SuppressionReason;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemorySuppressionEventRepository implements SuppressionEventRepository {

  private final List<SuppressionEvent> events = new ArrayList<>();

  @Override
  public SuppressionEvent create(SuppressionEvent event) {
    events.add(event);
    return event;
  }

  @Override
  public void lockDedup(SurveyVersionId versionId, SuppressionDedupKey dedupKey) {}

  @Override
  public boolean existsSince(
      SurveyVersionId versionId, SuppressionDedupKey dedupKey, Instant since) {
    return events.stream()
        .filter(event -> event.getVersionId().equals(versionId))
        .filter(event -> event.dedupKey().filter(dedupKey::equals).isPresent())
        .anyMatch(event -> event.getOccurredAt().isAfter(since));
  }

  @Override
  public SuppressionCounts countFor(SurveyId surveyId, Instant from, Instant to) {
    var matching =
        events.stream()
            .filter(event -> event.getSurveyId().equals(surveyId))
            .filter(event -> !event.getOccurredAt().isBefore(from))
            .filter(event -> !event.getOccurredAt().isAfter(to))
            .toList();

    var byReason = new EnumMap<SuppressionReason, Long>(SuppressionReason.class);
    var byVersion = new LinkedHashMap<Optional<SdkVersion>, Long>();
    for (var event : matching) {
      byReason.merge(event.getReason(), 1L, Long::sum);
      byVersion.merge(event.sdkVersion(), 1L, Long::sum);
    }

    var versions =
        byVersion.entrySet().stream()
            .sorted(
                Comparator.comparing(
                    (Map.Entry<Optional<SdkVersion>, Long> entry) -> entry.getKey(),
                    Comparator.comparing(
                        (Optional<SdkVersion> version) -> version.orElse(null),
                        Comparator.nullsFirst(Comparator.<SdkVersion>naturalOrder()))
                        .reversed()))
            .map(
                entry ->
                    new VersionCount(entry.getKey().map(SdkVersion::value), entry.getValue()))
            .toList();

    return new SuppressionCounts(matching.size(), byReason, versions);
  }

  public List<SuppressionEvent> findAll() {
    return List.copyOf(events);
  }
}
