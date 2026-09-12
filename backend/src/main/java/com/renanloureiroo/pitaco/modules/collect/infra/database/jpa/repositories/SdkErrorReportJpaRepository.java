package com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.repositories;

import com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.entities.SdkErrorReportJpaEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SdkErrorReportJpaRepository
    extends JpaRepository<SdkErrorReportJpaEntity, String> {

  @Modifying
  @Query(
      nativeQuery = true,
      value =
          """
          insert into sdk_error_reports
                 (id, application_id, sdk_version, kind, message, context, occurred_at,
                  received_at)
          values (:id, :applicationId, cast(:sdkVersion as varchar), :kind, :message,
                  cast(:context as jsonb), :occurredAt, :receivedAt)
          """)
  int insert(
      @Param("id") String id,
      @Param("applicationId") String applicationId,
      @Param("sdkVersion") String sdkVersion,
      @Param("kind") String kind,
      @Param("message") String message,
      @Param("context") String context,
      @Param("occurredAt") Instant occurredAt,
      @Param("receivedAt") Instant receivedAt);

  @Query(
      nativeQuery = true,
      value =
          """
          select id, sdk_version, kind, message, cast(context as text), occurred_at, received_at
            from sdk_error_reports
           where application_id = :applicationId
             and (cast(:kind as varchar) is null or kind = cast(:kind as varchar))
             and (cast(:sdkVersion as varchar) is null
                  or sdk_version = cast(:sdkVersion as varchar))
           order by received_at desc, id desc
           limit :limit offset :offset
          """)
  List<Object[]> findPage(
      @Param("applicationId") String applicationId,
      @Param("kind") String kind,
      @Param("sdkVersion") String sdkVersion,
      @Param("limit") int limit,
      @Param("offset") long offset);

  @Query(
      nativeQuery = true,
      value =
          """
          select count(*)
            from sdk_error_reports
           where application_id = :applicationId
             and (cast(:kind as varchar) is null or kind = cast(:kind as varchar))
             and (cast(:sdkVersion as varchar) is null
                  or sdk_version = cast(:sdkVersion as varchar))
          """)
  long countPage(
      @Param("applicationId") String applicationId,
      @Param("kind") String kind,
      @Param("sdkVersion") String sdkVersion);

  @Modifying
  @Query("delete from SdkErrorReportJpaEntity r where r.receivedAt < :threshold")
  int deleteReceivedBefore(@Param("threshold") Instant threshold);
}
