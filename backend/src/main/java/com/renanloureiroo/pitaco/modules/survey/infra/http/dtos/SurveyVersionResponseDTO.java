package com.renanloureiroo.pitaco.modules.survey.infra.http.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Uma versão da pesquisa")
public record SurveyVersionResponseDTO(
    @Schema(
            description = "Número da versão, a partir de 1",
            example = "1",
            requiredMode = Schema.RequiredMode.REQUIRED)
        int number,
    @Schema(
            description = "Estado da versão",
            allowableValues = {"draft", "published"},
            example = "published",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String status,
    @Schema(description = "Instante da publicação, em UTC; ausente no rascunho", nullable = true)
        Instant publishedAt,
    @Schema(
            description = "Natureza da mudança declarada; ausente na versão 1",
            allowableValues = {"cosmetic", "semantic"},
            nullable = true)
        String changeKind,
    @Schema(description = "Resumo textual da mudança; ausente na versão 1", nullable = true)
        String changeSummary,
    @Schema(
            description = "Versões no mesmo grupo têm respostas somáveis entre si",
            example = "1",
            requiredMode = Schema.RequiredMode.REQUIRED)
        int comparabilityGroup) {}
