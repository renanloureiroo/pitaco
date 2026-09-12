package com.renanloureiroo.pitaco.modules.collect.application.outputs;

import com.renanloureiroo.pitaco.core.catalog.SdkVersion;
import com.renanloureiroo.pitaco.modules.collect.domain.health.SdkErrorReport;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public record SdkErrorReportOutput(
    String id,
    Optional<String> sdkVersion,
    String kind,
    String message,
    Map<String, Object> context,
    Instant occurredAt,
    Instant receivedAt) {

  public static SdkErrorReportOutput of(SdkErrorReport report) {
    return new SdkErrorReportOutput(
        report.id().value(),
        report.sdkVersion().map(SdkVersion::value),
        report.getKind().wire(),
        report.getMessage(),
        report.getContext().values(),
        report.getOccurredAt(),
        report.getReceivedAt());
  }
}
