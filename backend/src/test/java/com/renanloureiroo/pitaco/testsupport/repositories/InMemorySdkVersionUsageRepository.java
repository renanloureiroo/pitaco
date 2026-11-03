package com.renanloureiroo.pitaco.testsupport.repositories;

import com.renanloureiroo.pitaco.core.catalog.SdkVersion;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.SdkVersionUsageRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InMemorySdkVersionUsageRepository implements SdkVersionUsageRepository {

  private record Key(ApplicationId applicationId, SdkVersion version) {}

  private record DayKey(ApplicationId applicationId, SdkVersion version, LocalDate day) {}

  private record Total(long requests, Instant firstSeenAt, Instant lastSeenAt) {}

  private final Map<Key, Total> totals = new LinkedHashMap<>();
  private final Map<DayKey, Long> daily = new LinkedHashMap<>();
  private final List<UsageDelta> increments = new ArrayList<>();

  private int failuresLeft;

  @Override
  public void increment(UsageDelta delta) {
    if (failuresLeft > 0) {
      failuresLeft--;
      throw new IllegalStateException("banco indisponível");
    }

    increments.add(delta);
    totals.merge(
        new Key(delta.applicationId(), delta.version()),
        new Total(delta.requests(), delta.firstSeenAt(), delta.lastSeenAt()),
        (current, added) ->
            new Total(
                current.requests() + added.requests(),
                current.firstSeenAt().isBefore(added.firstSeenAt())
                    ? current.firstSeenAt()
                    : added.firstSeenAt(),
                current.lastSeenAt().isAfter(added.lastSeenAt())
                    ? current.lastSeenAt()
                    : added.lastSeenAt()));
    daily.merge(
        new DayKey(delta.applicationId(), delta.version(), delta.day()),
        delta.requests(),
        Long::sum);
  }

  @Override
  public List<SdkVersionUsage> findByApplication(
      ApplicationId applicationId, LocalDate recentFrom) {
    return totals.entrySet().stream()
        .filter(entry -> entry.getKey().applicationId().equals(applicationId))
        .map(
            entry ->
                new SdkVersionUsage(
                    entry.getKey().version(),
                    entry.getValue().requests(),
                    daily.entrySet().stream()
                        .filter(day -> day.getKey().applicationId().equals(applicationId))
                        .filter(day -> day.getKey().version().equals(entry.getKey().version()))
                        .filter(day -> !day.getKey().day().isBefore(recentFrom))
                        .mapToLong(Map.Entry::getValue)
                        .sum(),
                    entry.getValue().firstSeenAt(),
                    entry.getValue().lastSeenAt()))
        .toList();
  }

  @Override
  public int purgeDailyBefore(LocalDate day) {
    var before = daily.size();
    daily.keySet().removeIf(key -> key.day().isBefore(day));
    return before - daily.size();
  }

  public InMemorySdkVersionUsageRepository withUsage(
      ApplicationId applicationId,
      String version,
      LocalDate day,
      long requests,
      Instant firstSeenAt,
      Instant lastSeenAt) {
    increment(
        new UsageDelta(
            applicationId, SdkVersion.of(version), day, requests, firstSeenAt, lastSeenAt));
    increments.removeLast();
    return this;
  }

  public InMemorySdkVersionUsageRepository failingTimes(int times) {
    this.failuresLeft = times;
    return this;
  }

  public List<UsageDelta> increments() {
    return List.copyOf(increments);
  }

  public int dailyRows() {
    return daily.size();
  }
}
