package com.renanloureiroo.pitaco.modules.survey.infra.http.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "Uma versão com o conteúdo congelado nela")
public record SurveyVersionDetailResponseDTO(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int number,
    @Schema(
            allowableValues = {"draft", "published"},
            requiredMode = Schema.RequiredMode.REQUIRED)
        String status,
    @Schema(nullable = true) Instant publishedAt,
    @Schema(
            allowableValues = {"cosmetic", "semantic"},
            nullable = true)
        String changeKind,
    @Schema(nullable = true) String changeSummary,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int comparabilityGroup,
    @Schema(
            description = "Perguntas congeladas nesta versão, na ordem de exibição",
            requiredMode = Schema.RequiredMode.REQUIRED)
        List<QuestionResponseDTO> questions,
    @Schema(description = "Disparo congelado nesta versão", nullable = true)
        TriggerResponseDTO trigger) {}
