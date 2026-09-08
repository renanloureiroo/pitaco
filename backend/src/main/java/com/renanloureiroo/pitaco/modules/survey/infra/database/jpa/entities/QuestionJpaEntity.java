package com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.entities;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "questions")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class QuestionJpaEntity {

  @Id
  @Column(length = 64, nullable = false, updatable = false)
  private String id;

  @Column(name = "question_key", length = 64, nullable = false, updatable = false)
  private String questionKey;

  @Column(length = 500, nullable = false)
  private String statement;

  @Column(length = 24, nullable = false)
  private String type;

  @Column(nullable = false)
  private int position;

  @Column(nullable = false)
  private boolean required;

  @Column(name = "range_min")
  private Integer rangeMin;

  @Column(name = "range_max")
  private Integer rangeMax;

  // Set com @OrderBy, não List: duas coleções em bag no mesmo join fetch quebrariam a consulta,
  // e a ordem vem da coluna position.
  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "question_id", nullable = false)
  @OrderBy("position asc")
  private Set<QuestionOptionJpaEntity> options = new LinkedHashSet<>();
}
