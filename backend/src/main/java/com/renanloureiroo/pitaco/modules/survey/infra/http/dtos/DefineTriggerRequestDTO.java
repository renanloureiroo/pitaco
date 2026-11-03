package com.renanloureiroo.pitaco.modules.survey.infra.http.dtos;

import com.renanloureiroo.pitaco.modules.survey.application.usecases.DefineTriggerUseCase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.Optional;

@Schema(description = "Quando a pesquisa aparece: evento, janela e proporção")
public record DefineTriggerRequestDTO(
    @NotBlank(message = "Nome do evento é obrigatório")
        @Pattern(
            regexp = "^[a-z][a-z0-9_.]{1,79}$",
            message =
                "Nome do evento deve começar por letra minúscula e usar apenas letras "
                    + "minúsculas, dígitos, ponto e sublinhado, com 2 a 80 caracteres")
        @Schema(
            description = "Evento da aplicação que provoca a exibição",
            example = "checkout.completed",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String eventName,
    @NotNull(message = "Início da janela é obrigatório")
        @Schema(
            description = "Instante em que a janela abre, em UTC; o início é inclusivo",
            example = "2026-09-08T14:32:10Z",
            requiredMode = Schema.RequiredMode.REQUIRED)
        Instant windowStart,
    @Schema(
            description = "Instante em que a janela fecha. Ausente significa tempo indeterminado",
            example = "2026-10-08T14:32:10Z",
            nullable = true)
        Instant windowEnd,
    @NotNull(message = "Proporção é obrigatória")
        @DecimalMin(value = "0.0", message = "Proporção deve estar entre 0 e 1")
        @DecimalMax(value = "1.0", message = "Proporção deve estar entre 0 e 1")
        @Schema(
            description = "Fração do público elegível que verá a pesquisa",
            example = "0.25",
            requiredMode = Schema.RequiredMode.REQUIRED)
        Double samplingRate) {

  public DefineTriggerUseCase.Input toInput(String applicationId, String surveyId) {
    return new DefineTriggerUseCase.Input(
        applicationId,
        surveyId,
        eventName,
        windowStart,
        Optional.ofNullable(windowEnd),
        samplingRate == null ? 0.0 : samplingRate);
  }
}
