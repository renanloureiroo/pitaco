package com.renanloureiroo.pitaco.modules.collect.application.outputs;

import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayOutcome;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.SurveyDisplay;
import java.time.Instant;

public record SurveyDisplayOutput(
    String displayId,
    String surveyId,
    String versionId,
    DisplayOutcome outcome,
    Instant openedAt) {

  public static SurveyDisplayOutput of(SurveyDisplay display) {
    return new SurveyDisplayOutput(
        display.id().value(),
        display.getSurveyId().value(),
        display.getVersionId().value(),
        display.getOutcome(),
        display.getOpenedAt());
  }
}
