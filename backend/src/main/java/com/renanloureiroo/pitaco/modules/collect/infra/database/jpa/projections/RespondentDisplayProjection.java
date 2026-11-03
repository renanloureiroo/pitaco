package com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.projections;

import java.time.Instant;

public record RespondentDisplayProjection(
    String id,
    String surveyId,
    String versionId,
    int versionNumber,
    int comparabilityGroup,
    String outcome,
    String sdkVersion,
    Instant openedAt,
    Instant closedAt) {}
