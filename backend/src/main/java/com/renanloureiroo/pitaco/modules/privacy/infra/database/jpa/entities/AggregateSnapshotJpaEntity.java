package com.renanloureiroo.pitaco.modules.privacy.infra.database.jpa.entities;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "aggregate_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AggregateSnapshotJpaEntity {

  @Id
  @Column(length = 64, nullable = false, updatable = false)
  private String id;

  @Column(name = "survey_id", length = 64, nullable = false, updatable = false)
  private String surveyId;

  @Column(name = "version_id", length = 64, nullable = false, updatable = false)
  private String versionId;

  @Column(length = 16, nullable = false, updatable = false)
  private String reason;

  @Column(name = "discarded_before", nullable = false, updatable = false)
  private Instant discardedBefore;

  @Column(name = "responding_displays", nullable = false, updatable = false)
  private int respondingDisplays;

  @Column(name = "computed_at", nullable = false, updatable = false)
  private Instant computedAt;

  @ElementCollection
  @CollectionTable(
      name = "aggregate_snapshot_counts",
      joinColumns = @JoinColumn(name = "snapshot_id"))
  private List<Count> counts = new ArrayList<>();

  @Embeddable
  @Getter
  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  @AllArgsConstructor
  public static class Count {

    @Column(name = "question_key", length = 64, nullable = false)
    private String questionKey;

    @Column(length = 8, nullable = false)
    private String dimension;

    @Column(length = 120, nullable = false)
    private String value;

    @Column(nullable = false)
    private long count;
  }
}
