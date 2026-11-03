package com.renanloureiroo.pitaco.modules.collect.infra.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("pitaco.health")
public record HealthProperties(SdkUsage sdkUsage, Suppressions suppressions, SdkErrors sdkErrors) {

  public record SdkUsage(
      Duration flushInterval, Duration recentWindow, Duration staleAfter, Duration dailyRetention) {}

  public record Suppressions(
      Duration dedupWindow, Duration defaultWindow, double relevantShare, long relevantMinimum) {}

  public record SdkErrors(Duration retention, String purgeCron) {}
}
