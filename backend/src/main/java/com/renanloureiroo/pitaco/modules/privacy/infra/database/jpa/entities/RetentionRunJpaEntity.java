package com.renanloureiroo.pitaco.modules.privacy.infra.database.jpa.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "retention_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class RetentionRunJpaEntity {

  @Id
  @Column(length = 64, nullable = false, updatable = false)
  private String id;

  @Column(name = "application_id", length = 64, nullable = false, updatable = false)
  private String applicationId;

  @Column(name = "answers_deleted", nullable = false, updatable = false)
  private int answersDeleted;

  @Column(name = "texts_cleared", nullable = false, updatable = false)
  private int textsCleared;

  @Column(name = "ran_at", nullable = false, updatable = false)
  private Instant ranAt;
}
