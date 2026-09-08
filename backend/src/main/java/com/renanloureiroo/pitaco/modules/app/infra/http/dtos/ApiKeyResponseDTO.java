package com.renanloureiroo.pitaco.modules.app.infra.http.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(
    description =
        "Dados públicos de uma chave. Não existe campo para o segredo nem para sua "
            + "representação irreversível — a ausência é estrutural")
public record ApiKeyResponseDTO(
    @Schema(
            description = "Identificador da chave",
            example = "8c1f0f4e-2a77-4a6d-9b31-6f0c2d9a4e15",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String id,
    @Schema(
            description = "Aplicação dona da chave",
            example = "3f9a1c72-5d84-4a1e-9b0f-2c6e8d5a7b31",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String applicationId,
    @Schema(
            description = "Rótulo informado na emissão",
            example = "app iOS",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String label,
    @Schema(
            description = "Prefixo público, não sensível — identifica a chave em log e conversa",
            example = "pit_9f2a7c4b",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String prefix,
    @Schema(
            description = "Estado da chave, derivado da presença do instante de revogação",
            allowableValues = {"active", "revoked"},
            example = "active",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String status,
    @Schema(
            description = "Instante da emissão, em UTC",
            example = "2026-09-08T14:32:10Z",
            requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,
    @Schema(
            description = "Instante da revogação, em UTC. Ausente enquanto a chave está válida",
            example = "2026-09-09T09:15:00Z",
            nullable = true)
        Instant revokedAt) {}
