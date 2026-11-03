package com.renanloureiroo.pitaco.modules.privacy.application.outputs;

import com.renanloureiroo.pitaco.modules.privacy.domain.entities.DeletionAudit;
import java.time.Instant;
import java.util.Optional;

public record DeletionAuditOutput(
    String id,
    int displaysDeleted,
    int answersDeleted,
    Optional<String> performedBy,
    Instant performedAt) {

  public static DeletionAuditOutput of(DeletionAudit audit) {
    return new DeletionAuditOutput(
        audit.id().value(),
        audit.getDisplaysDeleted(),
        audit.getAnswersDeleted(),
        audit.performedBy(),
        audit.getPerformedAt());
  }
}
