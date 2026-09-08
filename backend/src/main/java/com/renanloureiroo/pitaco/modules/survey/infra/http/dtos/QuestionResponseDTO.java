package com.renanloureiroo.pitaco.modules.survey.infra.http.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Uma pergunta da versão, na ordem de exibição")
public record QuestionResponseDTO(
    @Schema(
            description = "Identificador desta pergunta nesta versão",
            example = "8c1f0f4e-2a77-4a6d-9b31-6f0c2d9a4e15",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String id,
    @Schema(
            description =
                "Chave estável: atravessa versões e nunca muda, mesmo com o enunciado reescrito",
            example = "b7c9a1d2-3e45-4f67-8901-2a3b4c5d6e7f",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String key,
    @Schema(
            description = "Enunciado da pergunta",
            example = "O que achou do atendimento?",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String statement,
    @Schema(
            description = "Tipo da pergunta",
            allowableValues = {
              "single_choice",
              "multiple_choice",
              "rating",
              "scale",
              "nps",
              "free_text"
            },
            example = "single_choice",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String type,
    @Schema(
            description = "Posição na ordem de exibição, começando em 1",
            example = "1",
            requiredMode = Schema.RequiredMode.REQUIRED)
        int position,
    @Schema(
            description = "Se a resposta é obrigatória",
            example = "true",
            requiredMode = Schema.RequiredMode.REQUIRED)
        boolean required,
    @Schema(
            description = "Alternativas; vazio nos tipos que não aceitam opção",
            requiredMode = Schema.RequiredMode.REQUIRED)
        List<QuestionOptionDTO> options,
    @Schema(description = "Faixa numérica; ausente nos tipos que não a aceitam", nullable = true)
        ScaleRangeDTO range) {}
