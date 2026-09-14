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

// A coluna jsonb do payload fica fora do mapeamento, como em sdk_error_reports: o lote é gravado
// num comando nativo só, com cast explícito.
@Entity
@Table(name = "survey_display_events")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SurveyDisplayEventJpaEntity {

  @Id
  @Column(length = 64, nullable = false, updatable = false)
  private String id;

  @Column(name = "display_id", length = 64, nullable = false, updatable = false)
  private String displayId;

  @Column(nullable = false, updatable = false)
  private int seq;

  @Column(name = "catalog_version", nullable = false, updatable = false)
  private int catalogVersion;

  @Column(length = 40, nullable = false, updatable = false)
  private String type;

  @Column(name = "question_key", length = 64, updatable = false)
  private String questionKey;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  @Column(name = "elapsed_ms", nullable = false, updatable = false)
  private long elapsedMs;

  @Column(name = "received_at", nullable = false, updatable = false)
  private Instant receivedAt;
}
