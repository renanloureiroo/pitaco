package com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.entities;

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
@Table(name = "surveys")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SurveyJpaEntity {

  @Id
  @Column(length = 64, nullable = false, updatable = false)
  private String id;

  @Column(name = "application_id", length = 64, nullable = false, updatable = false)
  private String applicationId;

  @Column(length = 120, nullable = false)
  private String name;

  @Column(length = 16, nullable = false)
  private String lifecycle;

  @Column(name = "published_version_number")
  private Integer publishedVersionNumber;

  @Column(name = "draft_version_number")
  private Integer draftVersionNumber;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
