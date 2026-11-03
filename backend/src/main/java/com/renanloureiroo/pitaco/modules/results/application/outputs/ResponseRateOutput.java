package com.renanloureiroo.pitaco.modules.results.application.outputs;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public record ResponseRateOutput(
    long displayed,
    long completed,
    long dismissed,
    long abandoned,
    long inProgress,
    Optional<Double> rate,
    String definition,
    List<TimelinePoint> timeline) {

  public record TimelinePoint(LocalDate day, long displayed, long completed) {}
}
