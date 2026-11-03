package com.renanloureiroo.pitaco.testsupport.gateways;

import com.renanloureiroo.pitaco.modules.privacy.application.gateways.RetentionSchedule;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

// Roda sempre um intervalo fixo depois do instante consultado; desligada, não roda.
public class InMemoryRetentionSchedule implements RetentionSchedule {

  private Optional<Duration> interval = Optional.of(Duration.ofHours(1));

  public InMemoryRetentionSchedule runningAfter(Duration value) {
    this.interval = Optional.of(value);
    return this;
  }

  public InMemoryRetentionSchedule disabled() {
    this.interval = Optional.empty();
    return this;
  }

  @Override
  public Optional<Instant> nextRunAfter(Instant instant) {
    return interval.map(instant::plus);
  }
}
