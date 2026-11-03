package com.renanloureiroo.pitaco.modules.app.infra.database.jpa.entities;

import com.renanloureiroo.pitaco.modules.app.domain.entities.Status;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "applications")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ApplicationJpaEntity {

  @Id
  @Column(length = 64, nullable = false, updatable = false)
  private String id;

  @Column(length = 50, nullable = false, unique = true, updatable = false)
  private String slug;

  @Column(length = 120, nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(length = 20, nullable = false)
  private Status status;

  @Column(name = "quiet_period_days")
  private Integer quietPeriodDays;

  @Column(name = "retention_days")
  private Integer retentionDays;

  @Column(name = "open_text_retention_days")
  private Integer openTextRetentionDays;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
