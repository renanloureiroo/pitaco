package com.renanloureiroo.pitaco.modules.privacy.domain.entities;

import com.renanloureiroo.pitaco.core.entity.Entity;
import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.privacy.domain.retention.SnapshotCount;
import java.time.Instant;
import java.util.List;
import lombok.Getter;

// O que se aprendeu com as respostas que a retenção apagou, por versão. Só contagens aditivas:
// somar dois congelados, ou um congelado ao que continua vivo, dá o mesmo que contar tudo junto.
@Getter
public final class AggregateSnapshot extends Entity<AggregateSnapshotId> {

  private static final String EMPTY_CODE = "aggregate_snapshot.empty";

  private final SurveyId surveyId;
  private final SurveyVersionId versionId;
  private final Instant discardedBefore;
  private final int respondingDisplays;
  private final List<SnapshotCount> counts;
  private final Instant computedAt;

  private AggregateSnapshot(
      AggregateSnapshotId id,
      SurveyId surveyId,
      SurveyVersionId versionId,
      Instant discardedBefore,
      int respondingDisplays,
      List<SnapshotCount> counts,
      Instant computedAt) {
    super(id);

    if (counts == null || counts.isEmpty()) {
      throw new DomainException(
          ErrorType.VALIDATION, EMPTY_CODE, "Agregado congelado precisa de ao menos uma contagem");
    }

    this.surveyId = surveyId;
    this.versionId = versionId;
    this.discardedBefore = discardedBefore;
    this.respondingDisplays = respondingDisplays;
    this.counts = List.copyOf(counts);
    this.computedAt = computedAt;
  }

  public static AggregateSnapshot create(
      SurveyId surveyId,
      SurveyVersionId versionId,
      Instant discardedBefore,
      int respondingDisplays,
      List<SnapshotCount> counts,
      Instant computedAt) {
    return new AggregateSnapshot(
        AggregateSnapshotId.generate(),
        surveyId,
        versionId,
        discardedBefore,
        respondingDisplays,
        counts,
        computedAt);
  }
}
