package com.renanloureiroo.pitaco.modules.collect.application.outputs;

import com.renanloureiroo.pitaco.modules.collect.domain.entities.ObservedAttribute;
import java.time.Instant;
import java.util.List;

public record ObservedAttributeOutput(
    String name, Instant firstSeenAt, Instant lastSeenAt, List<ValueOutput> values) {

  public record ValueOutput(String value, Instant lastSeenAt) {}

  public static ObservedAttributeOutput of(ObservedAttribute attribute) {
    return new ObservedAttributeOutput(
        attribute.getName(),
        attribute.getFirstSeenAt(),
        attribute.getLastSeenAt(),
        attribute.getValues().stream()
            .map(value -> new ValueOutput(value.value(), value.lastSeenAt()))
            .toList());
  }
}
