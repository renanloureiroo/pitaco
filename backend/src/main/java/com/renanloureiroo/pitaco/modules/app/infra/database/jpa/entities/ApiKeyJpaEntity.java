package com.renanloureiroo.pitaco.modules.app.infra.database.jpa.entities;

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
@Table(name = "api_keys")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ApiKeyJpaEntity {

  @Id
  @Column(length = 64, nullable = false, updatable = false)
  private String id;

  @Column(name = "application_id", length = 64, nullable = false, updatable = false)
  private String applicationId;

  @Column(length = 80, nullable = false, updatable = false)
  private String label;

  @Column(length = 16, nullable = false, updatable = false)
  private String prefix;

  @Column(name = "secret_hash", length = 64, nullable = false, updatable = false)
  private String secretHash;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;
}
