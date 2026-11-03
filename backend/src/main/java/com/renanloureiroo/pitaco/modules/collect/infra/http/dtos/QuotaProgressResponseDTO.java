package com.renanloureiroo.pitaco.modules.collect.infra.http.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Quanto a pesquisa já coletou rumo à cota de respostas")
public record QuotaProgressResponseDTO(
    @Schema(
            description = "Cota que encerra a pesquisa sozinha. Ausente quando não há cota",
            example = "100",
            nullable = true)
        Integer responseQuota,
    @Schema(
            description =
                "Respostas concluídas em todas as versões. Pode passar um pouco da cota: quem já "
                    + "estava com a pesquisa aberta quando ela foi atingida conclui normalmente",
            example = "42",
            requiredMode = Schema.RequiredMode.REQUIRED)
        long completedResponses) {}
