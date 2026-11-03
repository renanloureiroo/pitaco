package com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.core.catalog.SdkVersion;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SdkVersionUsageRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class SdkVersionUsageRepositoryJpa implements SdkVersionUsageRepository {

  private final SdkVersionUsageJpaRepository repository;

  public SdkVersionUsageRepositoryJpa(SdkVersionUsageJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public void increment(UsageDelta delta) {
    var applicationId = delta.applicationId().value();
    var version = delta.version().value();

    repository.incrementTotal(
        UUID.randomUUID().toString(),
        applicationId,
        version,
        delta.requests(),
        delta.firstSeenAt(),
        delta.lastSeenAt());
    repository.incrementDay(applicationId, version, delta.day(), delta.requests());
  }

  @Override
  public List<SdkVersionUsage> findByApplication(
      ApplicationId applicationId, LocalDate recentFrom) {
    return repository.findUsage(applicationId.value(), recentFrom).stream()
        .map(
            row ->
                new SdkVersionUsage(
                    SdkVersion.of((String) row[0]),
                    NativeValues.count(row[1]),
                    NativeValues.count(row[2]),
                    NativeValues.instant(row[3]),
                    NativeValues.instant(row[4])))
        .toList();
  }

  @Override
  public int purgeDailyBefore(LocalDate day) {
    return repository.purgeDailyBefore(day);
  }
}
