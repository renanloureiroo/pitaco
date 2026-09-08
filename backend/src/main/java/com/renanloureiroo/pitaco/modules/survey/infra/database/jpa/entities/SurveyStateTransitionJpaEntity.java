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
@Table(name = "survey_state_transitions")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SurveyStateTransitionJpaEntity {

  @Id
  @Column(length = 64, nullable = false, updatable = false)
  private String id;

  @Column(name = "survey_id", length = 64, nullable = false, updatable = false)
  private String surveyId;

  @Column(name = "from_state", length = 16, nullable = false, updatable = false)
  private String fromState;

  @Column(name = "to_state", length = 16, nullable = false, updatable = false)
  private String toState;

  @Column(length = 24, nullable = false, updatable = false)
  private String reason;

  @Column(length = 120)
  private String actor;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;
}
