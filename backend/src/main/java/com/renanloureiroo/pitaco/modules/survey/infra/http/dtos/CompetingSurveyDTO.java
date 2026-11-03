package com.renanloureiroo.pitaco.modules.survey.infra.http.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Outra pesquisa no ar ou agendada que escuta o mesmo evento")
public record CompetingSurveyDTO(
    @Schema(
            description = "Identificador da pesquisa concorrente",
            example = "8c1f0f4e-2a77-4a6d-9b31-6f0c2d9a4e15",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String surveyId,
    @Schema(
            description = "Nome da pesquisa concorrente",
            example = "CSAT do suporte",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
    @Schema(
            description = "Prioridade dela no desempate; maior vence",
            example = "0",
            requiredMode = Schema.RequiredMode.REQUIRED)
        int priority) {}
