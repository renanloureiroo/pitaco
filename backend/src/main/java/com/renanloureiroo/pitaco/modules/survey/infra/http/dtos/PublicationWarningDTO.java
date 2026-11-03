package com.renanloureiroo.pitaco.modules.survey.infra.http.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Um aviso de publicação: informa, não impede")
public record PublicationWarningDTO(
    @Schema(
            description = "Identificador estável do aviso",
            allowableValues = {
              "segmentation.no_known_match",
              "segmentation.contradictory",
              "trigger.competing_surveys",
              "compatibility.unsupported_by_majority"
            },
            example = "trigger.competing_surveys",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
    @Schema(
            description = "Regra de segmentação que não alcança ninguém, quando é o caso",
            example = "5d2f0b8a-9c1e-4a3b-8f7d-6e5c4b3a2d1f",
            nullable = true)
        String ruleId,
    @Schema(
            description = "Atributo envolvido, nos avisos de segmentação",
            example = "plano",
            nullable = true)
        String attribute,
    @Schema(
            description = "Pesquisas que disputam o mesmo evento, no aviso de competição",
            nullable = true)
        List<CompetingSurveyDTO> competingSurveys,
    @Schema(
            description =
                "Versão mínima do SDK que renderiza a pesquisa, no aviso de compatibilidade",
            example = "1.2.0",
            nullable = true)
        String minRequiredVersion,
    @Schema(
            description =
                "Proporção do tráfego recente de versões que não suportam a pesquisa, de 0 a 1, "
                    + "no aviso de compatibilidade",
            example = "0.62",
            nullable = true)
        Double unsupportedShare) {}
