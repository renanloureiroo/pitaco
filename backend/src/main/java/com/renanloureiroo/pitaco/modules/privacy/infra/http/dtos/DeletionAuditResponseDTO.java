package com.renanloureiroo.pitaco.modules.privacy.infra.http.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Que uma exclusão aconteceu, quando e quanto saiu. Nunca quem foi excluído")
public record DeletionAuditResponseDTO(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
    @Schema(example = "3", requiredMode = Schema.RequiredMode.REQUIRED) int displaysDeleted,
    @Schema(example = "7", requiredMode = Schema.RequiredMode.REQUIRED) int answersDeleted,
    @Schema(
            description = "Quem executou. Sempre ausente até existir login por SSO",
            nullable = true)
        String performedBy,
    @Schema(example = "2026-09-12T16:00:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant performedAt) {}
