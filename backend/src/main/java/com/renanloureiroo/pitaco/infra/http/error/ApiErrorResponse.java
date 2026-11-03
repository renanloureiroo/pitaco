package com.renanloureiroo.pitaco.infra.http.error;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ApiError", description = "Erro no formato RFC 9457 com extensões do Pitaco")
public record ApiErrorResponse(
    @Schema(
            description = "URI que identifica o tipo do problema; ausente quando não há uma",
            example = "about:blank")
        String type,
    @Schema(
            description = "Resumo do tipo do problema",
            example = "Conflict",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String title,
    @Schema(
            description = "Código HTTP da resposta",
            example = "409",
            requiredMode = Schema.RequiredMode.REQUIRED)
        int status,
    @Schema(
            description = "Explicação do que aconteceu nesta requisição",
            example = "Já existe uma aplicação com o slug acme-app",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String detail,
    @Schema(
            description = "URI da requisição que originou o erro",
            example = "/api/applications",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String instance,
    @Schema(
            description = "Identificador estável do erro, seguro para o client tratar",
            example = "application.slug.conflict",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
    @Schema(
            description = "Correlação com o trace da requisição; ausente com tracing desligado",
            example = "c21f86e5471ac0e39a4cf1d3ce080c61")
        String traceId) {}
