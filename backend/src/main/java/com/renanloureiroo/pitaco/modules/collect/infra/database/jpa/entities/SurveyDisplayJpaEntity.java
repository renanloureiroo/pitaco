package com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.entities;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "survey_displays")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SurveyDisplayJpaEntity {

  @Id
  @Column(length = 64, nullable = false, updatable = false)
  private String id;

  @Column(name = "application_id", length = 64, nullable = false, updatable = false)
  private String applicationId;

  @Column(name = "respondent_id", length = 64, nullable = false, updatable = false)
  private String respondentId;

  @Column(name = "survey_id", length = 64, nullable = false, updatable = false)
  private String surveyId;

  @Column(name = "version_id", length = 64, nullable = false, updatable = false)
  private String versionId;

  @Column(name = "comparability_group", nullable = false, updatable = false)
  private int comparabilityGroup;

  @Column(length = 16, nullable = false)
  private String outcome;

  @Column(name = "sdk_version", length = 40, updatable = false)
  private String sdkVersion;

  @Column(name = "opened_at", nullable = false, updatable = false)
  private Instant openedAt;

  @Column(name = "closed_at")
  private Instant closedAt;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "survey_display_attributes",
      joinColumns = @JoinColumn(name = "display_id", nullable = false))
  private Set<SurveyDisplayAttributeJpaEntity> attributes = new LinkedHashSet<>();
}
