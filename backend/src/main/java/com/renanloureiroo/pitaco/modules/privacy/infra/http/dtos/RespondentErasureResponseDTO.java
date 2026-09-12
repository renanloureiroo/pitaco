package com.renanloureiroo.pitaco.modules.privacy.infra.http.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "O que a exclusão apagou. Nada sobre quem foi excluído")
public record RespondentErasureResponseDTO(
    @Schema(
            description =
                "Falso quando não havia respondente com essa identidade: nada foi apagado e "
                    + "nenhum registro novo foi criado",
            example = "true",
            requiredMode = Schema.RequiredMode.REQUIRED)
        boolean deleted,
    @Schema(example = "3", requiredMode = Schema.RequiredMode.REQUIRED) int displaysDeleted,
    @Schema(example = "7", requiredMode = Schema.RequiredMode.REQUIRED) int answersDeleted) {}
