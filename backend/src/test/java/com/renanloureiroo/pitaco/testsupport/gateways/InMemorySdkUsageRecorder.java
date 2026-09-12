package com.renanloureiroo.pitaco.testsupport.gateways;

import com.renanloureiroo.pitaco.core.catalog.SdkVersion;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.SdkUsageRecorder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class InMemorySdkUsageRecorder implements SdkUsageRecorder {

  public record Recorded(ApplicationId applicationId, SdkVersion version, Instant at) {}

  private final List<Recorded> recorded = new ArrayList<>();

  private boolean failing;

  @Override
  public void record(ApplicationId applicationId, SdkVersion version, Instant now) {
    if (failing) {
      throw new IllegalStateException("acumulador indisponível");
    }
    recorded.add(new Recorded(applicationId, version, now));
  }

  public InMemorySdkUsageRecorder failing() {
    this.failing = true;
    return this;
  }

  public List<Recorded> recorded() {
    return List.copyOf(recorded);
  }
}
