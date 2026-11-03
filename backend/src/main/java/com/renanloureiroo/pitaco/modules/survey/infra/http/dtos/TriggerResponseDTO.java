package com.renanloureiroo.pitaco.modules.survey.infra.http.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "O disparo da versão, com as regras de segmentação penduradas nele")
public record TriggerResponseDTO(
    @Schema(
            description = "Evento da aplicação que provoca a exibição",
            example = "checkout.completed",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String eventName,
    @Schema(
            description = "Instante em que a janela abre, em UTC",
            example = "2026-09-08T14:32:10Z",
            requiredMode = Schema.RequiredMode.REQUIRED)
        Instant windowStart,
    @Schema(
            description = "Instante em que a janela fecha. Ausente significa tempo indeterminado",
            example = "2026-10-08T14:32:10Z",
            nullable = true)
        Instant windowEnd,
    @Schema(
            description = "Fração do público elegível que verá a pesquisa",
            example = "0.25",
            requiredMode = Schema.RequiredMode.REQUIRED)
        double samplingRate,
    @Schema(
            description = "Regras que restringem o público",
            requiredMode = Schema.RequiredMode.REQUIRED)
        List<SegmentationRuleResponseDTO> rules) {}
