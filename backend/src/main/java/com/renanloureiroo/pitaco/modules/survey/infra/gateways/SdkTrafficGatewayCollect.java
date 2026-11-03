package com.renanloureiroo.pitaco.modules.survey.infra.gateways;

import com.renanloureiroo.pitaco.core.catalog.SdkVersion;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.survey.application.gateways.SdkTrafficGateway;
import com.renanloureiroo.pitaco.modules.survey.domain.publication.SdkTraffic;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SdkTrafficGatewayCollect implements SdkTrafficGateway {

  private final SurveySdkTrafficJpaRepository repository;

  public SdkTrafficGatewayCollect(SurveySdkTrafficJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public List<SdkTraffic> recentTraffic(ApplicationId applicationId, LocalDate since) {
    return repository.findRecentTraffic(applicationId.value(), since).stream()
        .map(
            row ->
                new SdkTraffic(SdkVersion.of((String) row[0]), ((Number) row[1]).longValue()))
        .toList();
  }
}
