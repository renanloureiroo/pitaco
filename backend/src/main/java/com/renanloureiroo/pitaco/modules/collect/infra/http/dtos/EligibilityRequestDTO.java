package com.renanloureiroo.pitaco.modules.collect.infra.http.dtos;

import com.renanloureiroo.pitaco.modules.collect.application.usecases.FindEligibleSurveyUseCase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.Optional;

@Schema(description = "Consulta se há pesquisa para este respondente agora")
public record EligibilityRequestDTO(
    @NotBlank(message = "Evento é obrigatório")
        @Size(max = 80, message = "Evento não pode passar de 80 caracteres")
        @Schema(
            description = "Evento observado no app, comparado por igualdade exata",
            example = "checkout.completed",
            maxLength = 80,
            requiredMode = Schema.RequiredMode.REQUIRED)
        String event,
    @Valid
        @NotNull(message = "Identificação do respondente é obrigatória")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        RespondentDTO respondent,
    @Size(max = 50, message = "No máximo 50 atributos por consulta")
        @Schema(
            description = "Atributos do respondente neste instante; nome ≤ 80, valor ≤ 200",
            example = "{\"plano\": \"premium\", \"pais\": \"BR\"}",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Map<
                @Size(max = 80, message = "Nome do atributo não pode passar de 80 caracteres")
                String,
                @Size(max = 200, message = "Valor do atributo não pode passar de 200 caracteres")
                String>
            attributes) {

  public FindEligibleSurveyUseCase.Input toInput(String applicationId) {
    return toInput(applicationId, null);
  }

  public FindEligibleSurveyUseCase.Input toInput(String applicationId, String sdkVersion) {
    return new FindEligibleSurveyUseCase.Input(
        applicationId,
        Optional.ofNullable(respondent).flatMap(RespondentDTO::referenceOrEmpty),
        Optional.ofNullable(respondent).flatMap(RespondentDTO::deviceIdOrEmpty),
        event,
        attributes == null ? Map.of() : attributes,
        Optional.ofNullable(sdkVersion));
  }
}
