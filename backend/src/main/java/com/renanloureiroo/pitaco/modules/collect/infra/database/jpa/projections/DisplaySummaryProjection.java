package com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.projections;

import java.time.Instant;

// Construída pela própria consulta JPQL: o número da versão vem da junção com survey_versions,
// uma por página (D-03).
public record DisplaySummaryProjection(
    String id,
    String versionId,
    int versionNumber,
    int comparabilityGroup,
    String outcome,
    String sdkVersion,
    Instant openedAt,
    Instant closedAt) {}
