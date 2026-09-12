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

  @Column(nullable = false)
  private int priority;

  @Column(name = "response_quota")
  private Integer responseQuota;

  @Column(name = "ignores_quiet_period", nullable = false)
  private boolean ignoresQuietPeriod;

  @Column(name = "template_kind", length = 8, updatable = false)
  private String templateKind;

  @Column(name = "free_text_notice_enabled", nullable = false)
  private boolean freeTextNoticeEnabled;

  @Column(name = "free_text_notice_text", length = 200)
  private String freeTextNoticeText;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;
}
