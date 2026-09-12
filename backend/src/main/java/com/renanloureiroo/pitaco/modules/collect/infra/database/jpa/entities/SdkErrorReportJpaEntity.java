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

// A coluna jsonb do contexto fica fora do mapeamento: é gravada e lida em SQL nativo, com cast
// explícito, em vez de depender do format mapper do Hibernate para JSON.
@Entity
@Table(name = "sdk_error_reports")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SdkErrorReportJpaEntity {

  @Id
  @Column(length = 64, nullable = false, updatable = false)
  private String id;

  @Column(name = "application_id", length = 64, nullable = false, updatable = false)
  private String applicationId;

  @Column(name = "sdk_version", length = 40, updatable = false)
  private String sdkVersion;

  @Column(length = 32, nullable = false, updatable = false)
  private String kind;

  @Column(length = 500, nullable = false, updatable = false)
  private String message;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  @Column(name = "received_at", nullable = false, updatable = false)
  private Instant receivedAt;
}
