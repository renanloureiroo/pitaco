package com.renanloureiroo.pitaco.modules.survey.infra.http.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Uma regra de segmentação pendurada no disparo")
public record SegmentationRuleResponseDTO(
    @Schema(
            description = "Identificador da regra",
            example = "8c1f0f4e-2a77-4a6d-9b31-6f0c2d9a4e15",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String id,
    @Schema(
            description = "Atributo do público avaliado",
            example = "plan",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String attribute,
    @Schema(
            description = "Comparação aplicada",
            allowableValues = {"equals", "not_equals", "present", "absent"},
            example = "equals",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String operation,
    @Schema(
            description = "Valor de comparação; ausente em present e absent",
            example = "pro",
            nullable = true)
        String value) {}
