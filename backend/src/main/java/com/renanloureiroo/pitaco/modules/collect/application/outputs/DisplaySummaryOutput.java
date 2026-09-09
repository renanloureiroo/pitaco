package com.renanloureiroo.pitaco.modules.collect.application.outputs;

import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayOutcome;
import java.time.Instant;
import java.util.Optional;

public record DisplaySummaryOutput(
    DisplayId id,
    SurveyVersionId versionId,
    int versionNumber,
    int comparabilityGroup,
    DisplayOutcome outcome,
    Optional<String> sdkVersion,
    Instant openedAt,
    Optional<Instant> closedAt) {}
