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
@Table(name = "deletion_audits")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class DeletionAuditJpaEntity {

  @Id
  @Column(length = 64, nullable = false, updatable = false)
  private String id;

  @Column(name = "application_id", length = 64, nullable = false, updatable = false)
  private String applicationId;

  @Column(name = "displays_deleted", nullable = false, updatable = false)
  private int displaysDeleted;

  @Column(name = "answers_deleted", nullable = false, updatable = false)
  private int answersDeleted;

  @Column(name = "performed_by", length = 120, updatable = false)
  private String performedBy;

  @Column(name = "performed_at", nullable = false, updatable = false)
  private Instant performedAt;
}
