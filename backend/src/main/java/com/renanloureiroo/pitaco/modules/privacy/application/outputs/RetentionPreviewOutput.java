package com.renanloureiroo.pitaco.modules.privacy.application.outputs;

import java.time.Instant;
import java.util.Optional;

public record RetentionPreviewOutput(
    boolean configured,
    Optional<Integer> answerRetentionDays,
    Optional<Integer> textRetentionDays,
    Optional<Instant> nextRunAt,
    Forecast nextRun,
    Forecast nextWeek,
    Optional<Instant> lastRunAt) {

  public record Forecast(long answers, long texts) {

    public static final Forecast NONE = new Forecast(0, 0);
  }

  public boolean firstDiscardPending() {
    return lastRunAt.isEmpty();
  }
}
