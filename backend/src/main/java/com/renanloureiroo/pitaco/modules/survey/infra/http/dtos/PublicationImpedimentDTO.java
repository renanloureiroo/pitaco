package com.renanloureiroo.pitaco.modules.survey.infra.http.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Uma pendência que impede a publicação, apontando o campo responsável")
public record PublicationImpedimentDTO(
    @Schema(
            description = "Identificador estável do impedimento",
            allowableValues = {
              "survey.no_questions",
              "question.statement_missing",
              "question.options_missing",
              "trigger.missing",
              "trigger.window_invalid"
            },
            example = "question.options_missing",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
    @Schema(
            description = "Campo onde a pendência está",
            example = "questions[2].options",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String field,
    @Schema(
            description = "Chave da pergunta, quando o impedimento é de uma pergunta específica",
            example = "b7c9a1d2-3e45-4f67-8901-2a3b4c5d6e7f",
            nullable = true)
        String questionKey) {}
