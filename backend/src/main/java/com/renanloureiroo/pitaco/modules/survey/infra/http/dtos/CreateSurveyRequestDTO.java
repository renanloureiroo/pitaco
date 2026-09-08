package com.renanloureiroo.pitaco.modules.survey.infra.http.dtos;

import com.renanloureiroo.pitaco.modules.survey.application.usecases.CreateSurveyUseCase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados para criar uma pesquisa em rascunho")
public record CreateSurveyRequestDTO(
    @NotBlank(message = "Nome é obrigatório")
        @Size(max = 120, message = "Nome não pode passar de 120 caracteres")
        @Schema(
            description = "Nome pelo qual a pesquisa é reconhecida na aplicação",
            example = "NPS pós-checkout",
            maxLength = 120,
            requiredMode = Schema.RequiredMode.REQUIRED)
        String name) {

  public CreateSurveyUseCase.Input toInput(String applicationId) {
    return new CreateSurveyUseCase.Input(applicationId, name);
  }
}
