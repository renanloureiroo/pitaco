package com.renanloureiroo.pitaco.modules.collect.infra.http.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

@Schema(description = "Um relatório de falha do SDK, já sanitizado")
public record SdkErrorReportResponseDTO(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
    @Schema(example = "1.4.2", nullable = true) String sdkVersion,
    @Schema(
            allowableValues = {
              "render_error",
              "network_error",
              "malformed_response",
              "storage_error",
              "unknown"
            },
            requiredMode = Schema.RequiredMode.REQUIRED)
        String kind,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message,
    @Schema(
            description = "Contexto que sobreviveu à sanitização",
            requiredMode = Schema.RequiredMode.REQUIRED)
        Map<String, Object> context,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant occurredAt,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant receivedAt) {}
