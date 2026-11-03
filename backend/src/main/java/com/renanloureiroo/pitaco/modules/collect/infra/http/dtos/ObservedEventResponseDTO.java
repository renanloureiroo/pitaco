package com.renanloureiroo.pitaco.modules.collect.infra.http.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Um evento que o SDK desta aplicação já consultou")
public record ObservedEventResponseDTO(
    @Schema(
            description = "Nome do evento, exatamente como o app hospedeiro o dispara",
            example = "checkout.completed",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
    @Schema(
            description = "Instante em que o evento foi visto pela primeira vez, em UTC",
            example = "2026-08-30T09:11:00Z",
            requiredMode = Schema.RequiredMode.REQUIRED)
        Instant firstSeenAt,
    @Schema(
            description =
                "Instante em que o evento foi visto pela última vez, em UTC, com precisão de "
                    + "alguns minutos",
            example = "2026-09-08T14:22:31Z",
            requiredMode = Schema.RequiredMode.REQUIRED)
        Instant lastSeenAt) {}
