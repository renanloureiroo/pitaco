package com.renanloureiroo.pitaco.modules.collect.application.outputs;

import com.renanloureiroo.pitaco.modules.collect.domain.entities.ObservedEvent;
import java.time.Instant;

public record ObservedEventOutput(String name, Instant firstSeenAt, Instant lastSeenAt) {

  public static ObservedEventOutput of(ObservedEvent event) {
    return new ObservedEventOutput(
        event.getName().value(), event.getFirstSeenAt(), event.getLastSeenAt());
  }
}
