package com.renanloureiroo.pitaco.modules.collect.application.repositories;

import com.renanloureiroo.pitaco.core.catalog.SdkVersion;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// Rollup, não log: o acumulado por versão e o total por dia, que é o que permite falar do tráfego
// recente sem que uma versão antiga de muito volume domine a conta para sempre.
public interface SdkVersionUsageRepository {

  void increment(UsageDelta delta);

  // recentRequestCount soma os dias a partir de recentFrom, inclusive.
  List<SdkVersionUsage> findByApplication(ApplicationId applicationId, LocalDate recentFrom);

  int purgeDailyBefore(LocalDate day);

  record UsageDelta(
      ApplicationId applicationId,
      SdkVersion version,
      LocalDate day,
      long requests,
      Instant firstSeenAt,
      Instant lastSeenAt) {}

  record SdkVersionUsage(
      SdkVersion version,
      long requestCount,
      long recentRequestCount,
      Instant firstSeenAt,
      Instant lastSeenAt) {}
}
