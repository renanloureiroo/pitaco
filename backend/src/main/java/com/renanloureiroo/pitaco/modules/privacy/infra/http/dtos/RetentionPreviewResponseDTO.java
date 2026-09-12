package com.renanloureiroo.pitaco.modules.privacy.infra.http.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "O que a política de retenção vai descartar, para dar tempo de exportar")
public record RetentionPreviewResponseDTO(
    @Schema(
            description = "Falso quando a aplicação não tem prazo: nada é descartado",
            requiredMode = Schema.RequiredMode.REQUIRED)
        boolean configured,
    @Schema(description = "Prazo geral, em dias; apaga a resposta", nullable = true)
        Integer answerRetentionDays,
    @Schema(
            description = "Prazo efetivo do texto livre, em dias; apaga só o texto",
            nullable = true)
        Integer textRetentionDays,
    @Schema(
            description = "Próxima execução do descarte. Ausente sem prazo ou com o descarte desligado",
            nullable = true)
        Instant nextRunAt,
    @Schema(description = "O que sai na próxima execução") ForecastDTO nextRun,
    @Schema(description = "O que terá saído até uma semana depois da próxima execução")
        ForecastDTO nextWeek,
    @Schema(description = "Último descarte que apagou alguma coisa", nullable = true)
        Instant lastRunAt,
    @Schema(description = "Verdadeiro enquanto nenhum descarte aconteceu nesta aplicação")
        boolean firstDiscardPending) {

  public record ForecastDTO(
      @Schema(description = "Respostas apagadas pelo prazo geral", example = "12") long answers,
      @Schema(description = "Textos livres apagados, inclusive os que saem com a resposta", example = "4")
          long texts) {}
}
