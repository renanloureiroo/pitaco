package com.renanloureiroo.pitaco.modules.app.application.outputs;

import com.renanloureiroo.pitaco.modules.app.domain.entities.Application;
import com.renanloureiroo.pitaco.modules.app.domain.entities.Status;
import java.time.Instant;
import java.util.Optional;

public record ApplicationOutput(
    String id,
    String slug,
    String name,
    Status status,
    Optional<Integer> quietPeriodDays,
    Optional<Integer> retentionDays,
    Optional<Integer> openTextRetentionDays,
    Instant createdAt,
    Instant updatedAt) {

  public static ApplicationOutput of(Application application) {
    return new ApplicationOutput(
        application.id().value(),
        application.getSlug().value(),
        application.getName().value(),
        application.getStatus(),
        application.quietPeriodDays(),
        application.retentionDays(),
        application.openTextRetentionDays(),
        application.getCreatedAt(),
        application.getUpdatedAt());
  }
}
