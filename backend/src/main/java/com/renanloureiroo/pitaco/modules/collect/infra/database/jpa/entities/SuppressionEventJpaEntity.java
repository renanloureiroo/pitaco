package com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "suppression_events")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SuppressionEventJpaEntity {

  @Id
  @Column(length = 64, nullable = false, updatable = false)
  private String id;

  @Column(name = "application_id", length = 64, nullable = false, updatable = false)
  private String applicationId;

  @Column(name = "survey_id", length = 64, nullable = false, updatable = false)
  private String surveyId;

  @Column(name = "version_id", length = 64, nullable = false, updatable = false)
  private String versionId;

  @Column(name = "respondent_id", length = 64, updatable = false)
  private String respondentId;

  @Column(name = "dedup_key", length = 64, updatable = false)
  private String dedupKey;

  @Column(name = "sdk_version", length = 40, updatable = false)
  private String sdkVersion;

  @Column(length = 32, nullable = false, updatable = false)
  private String reason;

  @Column(name = "min_required_version", length = 40, nullable = false, updatable = false)
  private String minRequiredVersion;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;
}
