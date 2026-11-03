package com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.entities;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
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
@Table(name = "survey_answers")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SurveyAnswerJpaEntity {

  @Id
  @Column(length = 64, nullable = false, updatable = false)
  private String id;

  @Column(name = "display_id", length = 64, nullable = false, updatable = false)
  private String displayId;

  @Column(name = "question_key", length = 64, nullable = false, updatable = false)
  private String questionKey;

  @Column(length = 16, nullable = false, updatable = false)
  private String status;

  @Column(name = "text_value", length = 2000, updatable = false)
  private String textValue;

  @Column(name = "numeric_value", updatable = false)
  private Integer numericValue;

  @Column(name = "answered_at", nullable = false, updatable = false)
  private Instant answeredAt;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "survey_answer_options",
      joinColumns = @JoinColumn(name = "answer_id", nullable = false))
  @OrderBy("position asc")
  private Set<SurveyAnswerOptionJpaEntity> options = new LinkedHashSet<>();
}
