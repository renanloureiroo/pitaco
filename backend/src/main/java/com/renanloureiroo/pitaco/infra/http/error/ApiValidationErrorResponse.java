package com.renanloureiroo.pitaco.infra.http.error;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(
    name = "ApiValidationError",
    description = "Erro de validação da requisição, com o motivo de cada campo recusado")
public record ApiValidationErrorResponse(
    @Schema(description = "URI que identifica o tipo do problema; ausente quando não há uma")
        String type,
    @Schema(
            description = "Resumo do tipo do problema",
            example = "Bad Request",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String title,
    @Schema(
            description = "Código HTTP da resposta",
            example = "400",
            requiredMode = Schema.RequiredMode.REQUIRED)
        int status,
    @Schema(
            description = "Explicação do que aconteceu nesta requisição",
            example = "Requisição inválida",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String detail,
    @Schema(
            description = "URI da requisição que originou o erro",
            example = "/api/applications",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String instance,
    @Schema(
            description = "Identificador estável do erro, seguro para o client tratar",
            example = "request.invalid",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
    @Schema(
            description = "Correlação com o trace da requisição; ausente com tracing desligado",
            example = "c21f86e5471ac0e39a4cf1d3ce080c61")
        String traceId,
    @Schema(
            description = "Motivo da recusa por campo",
            example = "{\"name\":\"Nome é obrigatório\"}",
            requiredMode = Schema.RequiredMode.REQUIRED)
        Map<String, String> errors) {}
