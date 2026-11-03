package com.renanloureiroo.pitaco.modules.collect.application.outputs;

import java.time.Instant;
import java.util.Optional;

public record SdkVersionUsageOutput(
    String version,
    long requestCount,
    long recentRequestCount,
    Optional<Double> recentShare,
    Instant firstSeenAt,
    Instant lastSeenAt,
    boolean stale) {}
