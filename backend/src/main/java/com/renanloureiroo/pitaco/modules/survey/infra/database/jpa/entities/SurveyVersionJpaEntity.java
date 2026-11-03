package com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.entities;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "survey_versions")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SurveyVersionJpaEntity {

  @Id
  @Column(length = 64, nullable = false, updatable = false)
  private String id;

  @Column(name = "survey_id", length = 64, nullable = false, updatable = false)
  private String surveyId;

  @Column(name = "number", nullable = false, updatable = false)
  private int number;

  @Column(length = 16, nullable = false)
  private String status;

  // As quatro colunas do disparo: ou todas preenchidas, ou nenhuma — o mapper verifica.
  @Column(name = "trigger_event_name", length = 80)
  private String triggerEventName;

  @Column(name = "trigger_window_start")
  private Instant triggerWindowStart;

  @Column(name = "trigger_window_end")
  private Instant triggerWindowEnd;

  @Column(name = "trigger_sampling_rate", precision = 5, scale = 4)
  private BigDecimal triggerSamplingRate;

  @Column(name = "change_kind", length = 16)
  private String changeKind;

  @Column(name = "change_summary", length = 500)
  private String changeSummary;

  @Column(name = "comparability_group", nullable = false)
  private int comparabilityGroup;

  @Column(name = "published_at")
  private Instant publishedAt;

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "version_id", nullable = false)
  @OrderBy("position asc")
  private Set<QuestionJpaEntity> questions = new LinkedHashSet<>();

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "version_id", nullable = false)
  private Set<SegmentationRuleJpaEntity> rules = new LinkedHashSet<>();
}
