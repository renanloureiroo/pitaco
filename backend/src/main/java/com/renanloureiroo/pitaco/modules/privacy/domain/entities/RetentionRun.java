package com.renanloureiroo.pitaco.modules.privacy.domain.entities;

import com.renanloureiroo.pitaco.core.entity.Entity;
import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import java.time.Instant;
import lombok.Getter;

// Uma execução da retenção que descartou alguma coisa. Execução sem efeito não vira linha: é o
// que deixa "já houve descarte?" ser respondido pela existência de uma.
@Getter
public final class RetentionRun extends Entity<RetentionRunId> {

  private static final String EMPTY_RUN_CODE = "retention_run.empty";

  private final ApplicationId applicationId;
  private final int answersDeleted;
  private final int textsCleared;
  private final Instant ranAt;

  private RetentionRun(
      RetentionRunId id,
      ApplicationId applicationId,
      int answersDeleted,
      int textsCleared,
      Instant ranAt) {
    super(id);

    if (answersDeleted < 0 || textsCleared < 0 || answersDeleted + textsCleared == 0) {
      throw new DomainException(
          ErrorType.VALIDATION,
          EMPTY_RUN_CODE,
          "Execução de retenção só é registrada quando descarta alguma coisa");
    }

    this.applicationId = applicationId;
    this.answersDeleted = answersDeleted;
    this.textsCleared = textsCleared;
    this.ranAt = ranAt;
  }

  public static RetentionRun record(
      ApplicationId applicationId, int answersDeleted, int textsCleared, Instant ranAt) {
    return new RetentionRun(
        RetentionRunId.generate(), applicationId, answersDeleted, textsCleared, ranAt);
  }

  public static RetentionRun restore(
      RetentionRunId id,
      ApplicationId applicationId,
      int answersDeleted,
      int textsCleared,
      Instant ranAt) {
    return new RetentionRun(id, applicationId, answersDeleted, textsCleared, ranAt);
  }
}
