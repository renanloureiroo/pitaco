package com.renanloureiroo.pitaco.modules.privacy.domain.entities;

import com.renanloureiroo.pitaco.core.entity.Entity;
import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import java.time.Instant;
import java.util.Optional;
import lombok.Getter;

// Que houve exclusão, quando e quanto saiu. Nunca quem: um registro que guardasse a referência
// excluída seria o mesmo dado num lugar diferente.
@Getter
public final class DeletionAudit extends Entity<DeletionAuditId> {

  private static final String COUNT_INVALID_CODE = "deletion_audit.count_invalid";

  private final ApplicationId applicationId;
  private final int displaysDeleted;
  private final int answersDeleted;
  private final String performedBy;
  private final Instant performedAt;

  private DeletionAudit(
      DeletionAuditId id,
      ApplicationId applicationId,
      int displaysDeleted,
      int answersDeleted,
      String performedBy,
      Instant performedAt) {
    super(id);

    if (displaysDeleted < 0 || answersDeleted < 0) {
      throw new DomainException(
          ErrorType.VALIDATION, COUNT_INVALID_CODE, "Contagem de exclusão não pode ser negativa");
    }

    this.applicationId = applicationId;
    this.displaysDeleted = displaysDeleted;
    this.answersDeleted = answersDeleted;
    this.performedBy = performedBy;
    this.performedAt = performedAt;
  }

  public static DeletionAudit record(
      ApplicationId applicationId, int displaysDeleted, int answersDeleted) {
    return new DeletionAudit(
        DeletionAuditId.generate(),
        applicationId,
        displaysDeleted,
        answersDeleted,
        null,
        Instant.now());
  }

  public static DeletionAudit restore(
      DeletionAuditId id,
      ApplicationId applicationId,
      int displaysDeleted,
      int answersDeleted,
      String performedBy,
      Instant performedAt) {
    return new DeletionAudit(
        id, applicationId, displaysDeleted, answersDeleted, performedBy, performedAt);
  }

  public Optional<String> performedBy() {
    return Optional.ofNullable(performedBy);
  }
}
