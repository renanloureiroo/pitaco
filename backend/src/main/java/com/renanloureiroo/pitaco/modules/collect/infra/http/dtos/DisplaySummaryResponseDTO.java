package com.renanloureiroo.pitaco.modules.collect.infra.http.dtos;

import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayOutcome;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Uma exibição de pesquisa, sem os atributos do instantâneo")
public record DisplaySummaryResponseDTO(
    @Schema(
            description = "Identificador da exibição",
            example = "d1f0a3c8-77b2-4e19-9a5c-0b3e6f8d2a41",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String id,
    @Schema(
            description = "Versão da pesquisa que foi exibida",
            example = "8c2b5e14-3a97-4d60-b1f8-5e7c9a0d4b62",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String versionId,
    @Schema(
            description = "Número da versão exibida, o que identifica a versão no painel",
            example = "3",
            requiredMode = Schema.RequiredMode.REQUIRED)
        int versionNumber,
    @Schema(
            description = "Grupo de comparabilidade da versão exibida",
            example = "2",
            requiredMode = Schema.RequiredMode.REQUIRED)
        int comparabilityGroup,
    @Schema(
            description = "Desfecho gravado da exibição",
            allowableValues = {"STARTED", "COMPLETED", "DISMISSED"},
            example = "COMPLETED",
            requiredMode = Schema.RequiredMode.REQUIRED)
        DisplayOutcome outcome,
    @Schema(
            description = "Versão do SDK que exibiu. Ausente quando não informada",
            example = "1.4.0",
            nullable = true)
        String sdkVersion,
    @Schema(
            description = "Instante da abertura, em UTC",
            example = "2026-09-08T14:22:31Z",
            requiredMode = Schema.RequiredMode.REQUIRED)
        Instant openedAt,
    @Schema(
            description = "Instante do fechamento. Presente se e somente se o desfecho é final",
            example = "2026-09-08T14:23:07Z",
            nullable = true)
        Instant closedAt) {}
