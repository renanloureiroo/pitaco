package com.renanloureiroo.pitaco.modules.results.application.outputs;

import java.time.Instant;
import java.util.Optional;

// O recorte ecoado de volta: número sem recorte visível é número mal lido.
public record ResultsFilterOutput(
    Optional<Instant> from,
    Optional<Instant> to,
    Optional<String> attribute,
    Optional<String> attributeValue,
    boolean attributeAbsent,
    Optional<Integer> versionNumber) {}
