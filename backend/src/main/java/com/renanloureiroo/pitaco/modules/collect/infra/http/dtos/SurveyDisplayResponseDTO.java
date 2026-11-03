package com.renanloureiroo.pitaco.modules.collect.infra.http.dtos;

import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayOutcome;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(name = "SurveyDisplay", description = "A exibição de exibição aberta")
public record SurveyDisplayResponseDTO(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String surveyId,
    @Schema(
            description = "A versão exibida, congelada na abertura",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String versionId,
    @Schema(example = "STARTED", requiredMode = Schema.RequiredMode.REQUIRED)
        DisplayOutcome outcome,
    @Schema(example = "2026-09-08T18:22:31Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant openedAt) {}
