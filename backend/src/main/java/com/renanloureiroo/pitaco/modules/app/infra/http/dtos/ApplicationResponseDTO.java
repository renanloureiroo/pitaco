package com.renanloureiroo.pitaco.modules.app.infra.http.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

// Prazo não configurado sai do JSON em vez de virar null: a ausência é o contrato, e
// serializar null convidaria o cliente a lê-lo como zero.
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
    description =
        "Uma aplicação com os prazos de política. Prazo ausente é prazo não configurado — a "
            + "resposta diz o que foi definido, nunca o que seria derivado")
public record ApplicationResponseDTO(
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
            description = "Intervalo de descanso em dias. Ausente quando não configurado",
            example = "15",
            nullable = true)
        Integer quietPeriodDays,
    @Schema(
            description = "Prazo de retenção em dias. Ausente quando não configurado",
            example = "180",
            nullable = true)
        Integer retentionDays,
    @Schema(
            description =
                "Prazo de retenção de texto livre em dias. Ausente quando não configurado",
            example = "30",
            nullable = true)
        Integer openTextRetentionDays,
    @Schema(
            description = "Instante da criação, em UTC",
            example = "2026-09-08T14:32:10Z",
            requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,
    @Schema(
            description = "Instante da última alteração, em UTC",
            example = "2026-09-08T16:05:44Z",
            requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt) {}
