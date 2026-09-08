package com.renanloureiroo.pitaco.modules.survey.infra.http.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Dados de uma pesquisa, com o estado derivado no instante da leitura")
public record SurveyResponseDTO(
    @Schema(
            description = "Identificador da pesquisa",
            example = "8c1f0f4e-2a77-4a6d-9b31-6f0c2d9a4e15",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String id,
    @Schema(
            description = "Aplicação dona da pesquisa",
            example = "3f9a1c72-5d84-4a1e-9b0f-2c6e8d5a7b31",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String applicationId,
    @Schema(
            description = "Nome informado na criação",
            example = "NPS pós-checkout",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
    @Schema(
            description =
                "Estado derivado do ciclo de vida e da janela. scheduled vira active quando a "
                    + "janela abre, e active vira ended quando ela fecha; paused ignora a janela",
            allowableValues = {"draft", "scheduled", "active", "paused", "ended"},
            example = "draft",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String state,
    @Schema(
            description = "Versão publicada corrente. Ausente enquanto a pesquisa é rascunho",
            example = "1",
            nullable = true)
        Integer publishedVersionNumber,
    @Schema(
            description = "Rascunho de versão aberto. Ausente quando não há nenhum",
            example = "2",
            nullable = true)
        Integer draftVersionNumber,
    @Schema(
            description = "Instante da criação, em UTC",
            example = "2026-09-08T14:32:10Z",
            requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt) {}
