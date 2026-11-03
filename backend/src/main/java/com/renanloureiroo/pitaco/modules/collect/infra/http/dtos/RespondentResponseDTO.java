package com.renanloureiroo.pitaco.modules.collect.infra.http.dtos;

import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.RespondentIdentityKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Quem foi visto pela aplicação, identificado pelo par opaco que o SDK informou")
public record RespondentResponseDTO(
    @Schema(
            description = "Identificador do respondente",
            example = "b5c9e740-1a83-4f2d-9e06-7c4b1a8f3d25",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String id,
    @Schema(
            description =
                "APP_REFERENCE quando a aplicação informou a própria referência, DEVICE quando "
                    + "só o identificador do dispositivo foi informado",
            allowableValues = {"APP_REFERENCE", "DEVICE"},
            example = "APP_REFERENCE",
            requiredMode = Schema.RequiredMode.REQUIRED)
        RespondentIdentityKind identityKind,
    @Schema(
            description = "Valor da identificação, opaco para o Pitaco",
            example = "user-8821",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String identityValue,
    @Schema(
            description = "Instante do primeiro contato, em UTC",
            example = "2026-08-30T09:11:00Z",
            requiredMode = Schema.RequiredMode.REQUIRED)
        Instant firstSeenAt,
    @Schema(
            description = "Instante do último contato, em UTC",
            example = "2026-09-08T14:22:31Z",
            requiredMode = Schema.RequiredMode.REQUIRED)
        Instant lastSeenAt) {}
