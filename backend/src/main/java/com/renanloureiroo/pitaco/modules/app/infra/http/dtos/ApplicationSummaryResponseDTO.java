package com.renanloureiroo.pitaco.modules.app.infra.http.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Uma aplicação como aparece na listagem, sem os prazos de política")
public record ApplicationSummaryResponseDTO(
    @Schema(
            description = "Identificador da aplicação",
            example = "3f9a1c72-5d84-4a1e-9b0f-2c6e8d5a7b31",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String id,
    @Schema(
            description = "Identificador legível, público e imutável",
            example = "acme-app",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String slug,
    @Schema(
            description = "Nome de exibição",
            example = "Acme App",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
    @Schema(
            description = "Estado da aplicação",
            allowableValues = {"active", "inactive"},
            example = "active",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String status,
    @Schema(
            description = "Instante da criação, em UTC",
            example = "2026-09-08T14:32:10Z",
            requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt) {}
