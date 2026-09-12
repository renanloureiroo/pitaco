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

// O total por dia, em sdk_version_daily_usage, não tem entidade: só é tocado por upsert e por
// agregação, sempre em SQL nativo.
@Entity
@Table(name = "sdk_version_usage")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class SdkVersionUsageJpaEntity {

  @Id
  @Column(length = 64, nullable = false, updatable = false)
  private String id;

  @Column(name = "application_id", length = 64, nullable = false, updatable = false)
  private String applicationId;

  @Column(length = 40, nullable = false, updatable = false)
  private String version;

  @Column(name = "request_count", nullable = false)
  private long requestCount;

  @Column(name = "first_seen_at", nullable = false)
  private Instant firstSeenAt;

  @Column(name = "last_seen_at", nullable = false)
  private Instant lastSeenAt;
}
