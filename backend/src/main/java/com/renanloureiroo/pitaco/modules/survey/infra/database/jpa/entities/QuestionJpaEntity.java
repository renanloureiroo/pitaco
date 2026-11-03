package com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.entities;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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

  @Column(name = "range_min_label", length = 60)
  private String rangeMinLabel;

  @Column(name = "range_max_label", length = 60)
  private String rangeMaxLabel;

  @Column(name = "condition_source_key", length = 64)
  private String conditionSourceKey;

  @Column(name = "condition_operator", length = 16)
  private String conditionOperator;

  @Column(name = "condition_min")
  private Integer conditionMin;

  @Column(name = "condition_max")
  private Integer conditionMax;

  // Set com @OrderBy, não List: duas coleções em bag no mesmo join fetch quebrariam a consulta,
  // e a ordem vem da coluna position.
  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "question_id", nullable = false)
  @OrderBy("position asc")
  private Set<QuestionOptionJpaEntity> options = new LinkedHashSet<>();

  // Lista indexada pela coluna position, e por isso não é bag: cabe no mesmo join fetch das
  // opções sem o produto cartesiano duplicar valor.
  @ElementCollection
  @CollectionTable(name = "question_condition_values", joinColumns = @JoinColumn(name = "question_id"))
  @OrderColumn(name = "position")
  @Column(name = "value", length = 120, nullable = false)
  private List<String> conditionValues = new ArrayList<>();
}
