package com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Embeddable e não @Entity: o atributo não tem identidade própria — existe enquanto a exibição
// existir, e a chave é (display_id, name).
@Embeddable
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode
public class SurveyDisplayAttributeJpaEntity {

  @Column(name = "name", length = 80, nullable = false)
  private String name;

  @Column(name = "value", length = 200, nullable = false)
  private String value;
}
