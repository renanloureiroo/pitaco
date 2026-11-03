package com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.entities.SdkVersionUsageJpaEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SdkVersionUsageJpaRepository
    extends JpaRepository<SdkVersionUsageJpaEntity, String> {

  // Soma, não substitui: dois processos ou duas descargas seguidas nunca perdem contagem, e a
  // chave única resolve a corrida da primeira ocorrência.
  @Modifying
  @Query(
      nativeQuery = true,
      value =
          """
          insert into sdk_version_usage
                 (id, application_id, version, request_count, first_seen_at, last_seen_at)
          values (:id, :applicationId, :version, :requests, :firstSeenAt, :lastSeenAt)
          on conflict (application_id, version) do update
             set request_count = sdk_version_usage.request_count + excluded.request_count,
                 first_seen_at = least(sdk_version_usage.first_seen_at, excluded.first_seen_at),
                 last_seen_at = greatest(sdk_version_usage.last_seen_at, excluded.last_seen_at)
          """)
  int incrementTotal(
      @Param("id") String id,
      @Param("applicationId") String applicationId,
      @Param("version") String version,
      @Param("requests") long requests,
      @Param("firstSeenAt") Instant firstSeenAt,
      @Param("lastSeenAt") Instant lastSeenAt);

  @Modifying
  @Query(
      nativeQuery = true,
      value =
          """
          insert into sdk_version_daily_usage (application_id, version, day, request_count)
          values (:applicationId, :version, :day, :requests)
          on conflict (application_id, version, day) do update
             set request_count = sdk_version_daily_usage.request_count + excluded.request_count
          """)
  int incrementDay(
      @Param("applicationId") String applicationId,
      @Param("version") String version,
      @Param("day") LocalDate day,
      @Param("requests") long requests);

  @Query(
      nativeQuery = true,
      value =
          """
          select u.version,
                 u.request_count,
                 cast(coalesce(sum(d.request_count), 0) as bigint),
                 u.first_seen_at,
                 u.last_seen_at
            from sdk_version_usage u
            left join sdk_version_daily_usage d
                   on d.application_id = u.application_id
                  and d.version = u.version
                  and d.day >= :recentFrom
           where u.application_id = :applicationId
           group by u.id, u.version, u.request_count, u.first_seen_at, u.last_seen_at
          """)
  List<Object[]> findUsage(
      @Param("applicationId") String applicationId, @Param("recentFrom") LocalDate recentFrom);

  @Modifying
  @Query(nativeQuery = true, value = "delete from sdk_version_daily_usage where day < :day")
  int purgeDailyBefore(@Param("day") LocalDate day);
}
