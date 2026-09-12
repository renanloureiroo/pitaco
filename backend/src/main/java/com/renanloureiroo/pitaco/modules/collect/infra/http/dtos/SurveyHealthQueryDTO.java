package com.renanloureiroo.pitaco.modules.collect.infra.http.dtos;

import com.renanloureiroo.pitaco.modules.collect.application.usecases.GetSurveyHealthUseCase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import java.time.Instant;
import java.util.Optional;

@Schema(description = "Período da leitura de saúde; sem ele, os últimos 30 dias")
public record SurveyHealthQueryDTO(
    @Schema(description = "Início do período, em UTC e inclusive", example = "2026-08-13T00:00:00Z")
        Instant from,
    @Schema(description = "Fim do período, em UTC e inclusive", example = "2026-09-12T23:59:59Z")
        Instant to) {

  @AssertTrue(message = "Início do período não pode ser posterior ao fim")
  @Schema(hidden = true)
  public boolean isPeriodOrdered() {
    return from == null || to == null || !from.isAfter(to);
  }

  public GetSurveyHealthUseCase.Input toInput(String applicationId, String surveyId) {
    return new GetSurveyHealthUseCase.Input(
        applicationId, surveyId, Optional.ofNullable(from), Optional.ofNullable(to));
  }
}
