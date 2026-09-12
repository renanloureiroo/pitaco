package com.renanloureiroo.pitaco.modules.privacy.infra.database.jpa.mappers;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.privacy.domain.entities.AggregateSnapshot;
import com.renanloureiroo.pitaco.modules.privacy.domain.entities.DeletionAudit;
import com.renanloureiroo.pitaco.modules.privacy.domain.entities.DeletionAuditId;
import com.renanloureiroo.pitaco.modules.privacy.domain.entities.RetentionRun;
import com.renanloureiroo.pitaco.modules.privacy.infra.database.jpa.entities.AggregateSnapshotJpaEntity;
import com.renanloureiroo.pitaco.modules.privacy.infra.database.jpa.entities.DeletionAuditJpaEntity;
import com.renanloureiroo.pitaco.modules.privacy.infra.database.jpa.entities.RetentionRunJpaEntity;
import java.util.ArrayList;

public final class PrivacyJpaMapper {

  private static final String RETENTION_REASON = "RETENTION";

  private PrivacyJpaMapper() {}

  public static DeletionAuditJpaEntity toJpa(DeletionAudit audit) {
    return new DeletionAuditJpaEntity(
        audit.id().value(),
        audit.getApplicationId().value(),
        audit.getDisplaysDeleted(),
        audit.getAnswersDeleted(),
        audit.performedBy().orElse(null),
        audit.getPerformedAt());
  }

  public static DeletionAudit toDomain(DeletionAuditJpaEntity entity) {
    return DeletionAudit.restore(
        DeletionAuditId.of(entity.getId()),
        ApplicationId.of(entity.getApplicationId()),
        entity.getDisplaysDeleted(),
        entity.getAnswersDeleted(),
        entity.getPerformedBy(),
        entity.getPerformedAt());
  }

  public static RetentionRunJpaEntity toJpa(RetentionRun run) {
    return new RetentionRunJpaEntity(
        run.id().value(),
        run.getApplicationId().value(),
        run.getAnswersDeleted(),
        run.getTextsCleared(),
        run.getRanAt());
  }

  public static AggregateSnapshotJpaEntity toJpa(AggregateSnapshot snapshot) {
    return new AggregateSnapshotJpaEntity(
        snapshot.id().value(),
        snapshot.getSurveyId().value(),
        snapshot.getVersionId().value(),
        RETENTION_REASON,
        snapshot.getDiscardedBefore(),
        snapshot.getRespondingDisplays(),
        snapshot.getComputedAt(),
        new ArrayList<>(
            snapshot.getCounts().stream()
                .map(
                    count ->
                        new AggregateSnapshotJpaEntity.Count(
                            count.key().value(),
                            count.dimension().name(),
                            count.value(),
                            count.count()))
                .toList()));
  }
}
