package com.renanloureiroo.pitaco.modules.survey.infra.http.dtos;

import com.renanloureiroo.pitaco.modules.survey.application.usecases.RenameSurveyUseCase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Novo nome da pesquisa")
public record RenameSurveyRequestDTO(
    @NotBlank(message = "Nome é obrigatório")
        @Size(max = 120, message = "Nome não pode passar de 120 caracteres")
        @Schema(
            description = "Nome pelo qual a pesquisa passa a ser reconhecida",
            example = "NPS pós-entrega",
            maxLength = 120,
            requiredMode = Schema.RequiredMode.REQUIRED)
        String name) {

  public RenameSurveyUseCase.Input toInput(String applicationId, String surveyId) {
    return new RenameSurveyUseCase.Input(applicationId, surveyId, name);
  }
}
