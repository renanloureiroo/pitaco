package com.renanloureiroo.pitaco.modules.collect.application.outputs;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record SurveyHealthOutput(
    Instant from,
    Instant to,
    long displays,
    long suppressions,
    List<ReasonCount> suppressionsByReason,
    List<VersionCount> suppressionsBySdkVersion,
    Optional<Double> suppressionShare,
    boolean relevant,
    Optional<String> minRequiredVersion,
    Optional<String> eventName,
    Optional<Instant> eventLastSeenAt) {

  public record ReasonCount(String reason, long count) {}

  public record VersionCount(Optional<String> sdkVersion, long count) {}
}
