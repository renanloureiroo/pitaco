package com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.core.catalog.SdkVersion;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SuppressionEventRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.RespondentId;
import com.renanloureiroo.pitaco.modules.collect.domain.health.SuppressionDedupKey;
import com.renanloureiroo.pitaco.modules.collect.domain.health.SuppressionEvent;
import com.renanloureiroo.pitaco.modules.collect.domain.health.SuppressionReason;
import com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.entities.SuppressionEventJpaEntity;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class SuppressionEventRepositoryJpa implements SuppressionEventRepository {

  private final SuppressionEventJpaRepository repository;

  public SuppressionEventRepositoryJpa(SuppressionEventJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public SuppressionEvent create(SuppressionEvent event) {
    repository.save(
        new SuppressionEventJpaEntity(
            event.id().value(),
            event.getApplicationId().value(),
            event.getSurveyId().value(),
            event.getVersionId().value(),
            event.respondentId().map(RespondentId::value).orElse(null),
            event.dedupKey().map(SuppressionDedupKey::value).orElse(null),
            event.sdkVersion().map(SdkVersion::value).orElse(null),
            event.getReason().name(),
            event.getMinRequiredVersion().value(),
            event.getOccurredAt()));
    return event;
  }

  @Override
  public void lockDedup(SurveyVersionId versionId, SuppressionDedupKey dedupKey) {
    repository.lockDedup(versionId.value() + "|" + dedupKey.value());
  }

  @Override
  public boolean existsSince(
      SurveyVersionId versionId, SuppressionDedupKey dedupKey, Instant since) {
    return repository.existsByVersionIdAndDedupKeyAndOccurredAtAfter(
        versionId.value(), dedupKey.value(), since);
  }

  @Override
  public SuppressionCounts countFor(SurveyId surveyId, Instant from, Instant to) {
    var byReason = new EnumMap<SuppressionReason, Long>(SuppressionReason.class);
    var total = 0L;
    for (var row : repository.countByReason(surveyId.value(), from, to)) {
      var count = ((Number) row[1]).longValue();
      byReason.put(SuppressionReason.valueOf((String) row[0]), count);
      total += count;
    }

    // Da mais nova para a mais antiga pela ordem semver, que o banco não sabe fazer; a ausência
    // de versão vai por último.
    var byVersion =
        repository.countBySdkVersion(surveyId.value(), from, to).stream()
            .map(
                row ->
                    new VersionCount(
                        Optional.ofNullable((String) row[0]), ((Number) row[1]).longValue()))
            .sorted(
                Comparator.comparing(
                    (VersionCount count) -> count.sdkVersion().flatMap(SdkVersion::parse),
                    (left, right) -> {
                      if (left.isEmpty() || right.isEmpty()) {
                        return Boolean.compare(left.isEmpty(), right.isEmpty());
                      }
                      return right.get().compareTo(left.get());
                    }))
            .toList();

    return new SuppressionCounts(total, byReason, byVersion);
  }
}
