package com.renanloureiroo.pitaco.modules.survey.infra.http.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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
        Integer max,
    @Size(max = 60, message = "Rótulo da escala não pode passar de 60 caracteres")
        @Schema(
            description = "Texto exibido junto do menor valor",
            example = "Nada provável",
            maxLength = 60,
            nullable = true)
        String minLabel,
    @Size(max = 60, message = "Rótulo da escala não pode passar de 60 caracteres")
        @Schema(
            description = "Texto exibido junto do maior valor",
            example = "Extremamente provável",
            maxLength = 60,
            nullable = true)
        String maxLabel) {

  public ScaleRangeDTO(Integer min, Integer max) {
    this(min, max, null, null);
  }
}
