package com.renanloureiroo.pitaco.testsupport.gateways;

import com.renanloureiroo.pitaco.core.catalog.SdkVersion;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.survey.application.gateways.SdkTrafficGateway;
import com.renanloureiroo.pitaco.modules.survey.domain.publication.SdkTraffic;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InMemorySdkTrafficGateway implements SdkTrafficGateway {

  private final Map<ApplicationId, List<SdkTraffic>> traffic = new HashMap<>();

  @Override
  public List<SdkTraffic> recentTraffic(ApplicationId applicationId, LocalDate since) {
    return List.copyOf(traffic.getOrDefault(applicationId, List.of()));
  }

  public InMemorySdkTrafficGateway withTraffic(
      ApplicationId applicationId, String version, long requests) {
    traffic
        .computeIfAbsent(applicationId, key -> new ArrayList<>())
        .add(new SdkTraffic(SdkVersion.of(version), requests));
    return this;
  }
}
