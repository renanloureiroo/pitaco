package com.renanloureiroo.pitaco.modules.survey.infra.http.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(
    description =
        "Uma transição de estado. As comandadas ficam registradas; as de janela são derivadas "
            + "do limite já passado e calculadas na leitura")
public record SurveyStateTransitionResponseDTO(
    @Schema(
            description = "Estado de origem",
            allowableValues = {"draft", "scheduled", "active", "paused", "ended"},
            requiredMode = Schema.RequiredMode.REQUIRED)
        String from,
    @Schema(
            description = "Estado de destino",
            allowableValues = {"draft", "scheduled", "active", "paused", "ended"},
            requiredMode = Schema.RequiredMode.REQUIRED)
        String to,
    @Schema(
            description = "Motivo da transição",
            allowableValues = {
              "publication",
              "manual_pause",
              "manual_resume",
              "manual_end",
              "quota_reached",
              "window_opened",
              "window_closed"
            },
            requiredMode = Schema.RequiredMode.REQUIRED)
        String reason,
    @Schema(
            description = "Reservado; sempre ausente enquanto não houver autenticação",
            nullable = true)
        String actor,
    @Schema(
            description = "Instante da transição, em UTC. Nas de janela, é o limite da janela",
            requiredMode = Schema.RequiredMode.REQUIRED)
        Instant occurredAt) {}
