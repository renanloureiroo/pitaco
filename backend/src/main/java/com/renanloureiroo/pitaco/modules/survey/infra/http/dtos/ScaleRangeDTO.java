package com.renanloureiroo.pitaco.modules.survey.infra.http.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Faixa numérica das perguntas de avaliação, escala e NPS")
public record ScaleRangeDTO(
    @NotNull(message = "Mínimo da faixa é obrigatório")
        @Schema(
            description = "Menor valor aceito",
            example = "0",
            requiredMode = Schema.RequiredMode.REQUIRED)
        Integer min,
    @NotNull(message = "Máximo da faixa é obrigatório")
        @Schema(
            description = "Maior valor aceito",
            example = "10",
            requiredMode = Schema.RequiredMode.REQUIRED)
        Integer max) {}
