package com.renanloureiroo.pitaco.modules.survey.infra.gateways;

import com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.entities.SdkVersionUsageJpaEntity;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Do lado da autoria, como o SurveyAttributeCatalogJpaRepository: é necessidade de quem lê.
public interface SurveySdkTrafficJpaRepository
    extends JpaRepository<SdkVersionUsageJpaEntity, String> {

  @Query(
      nativeQuery = true,
      value =
          """
          select version, cast(sum(request_count) as bigint)
            from sdk_version_daily_usage
           where application_id = :applicationId
             and day >= :since
           group by version
          """)
  List<Object[]> findRecentTraffic(
      @Param("applicationId") String applicationId, @Param("since") LocalDate since);
}
