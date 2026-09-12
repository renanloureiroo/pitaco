package com.renanloureiroo.pitaco.modules.survey.infra.http.dtos;

import com.renanloureiroo.pitaco.modules.survey.application.usecases.DuplicateSurveyUseCase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Optional;

@Schema(description = "Para onde e com que nome copiar a pesquisa. Os dois campos são opcionais")
public record DuplicateSurveyRequestDTO(
    @Schema(
            description = "Aplicação que recebe a cópia. Ausente, a cópia fica na mesma aplicação",
            example = "3f9a1c72-5d84-4a1e-9b0f-2c6e8d5a7b31",
            nullable = true)
        String targetApplicationId,
    @Size(max = 120, message = "Nome não pode passar de 120 caracteres")
        @Pattern(regexp = ".*\\S.*", message = "Nome não pode ser vazio")
        @Schema(
            description = "Nome da cópia. Ausente, vira \"Cópia de\" seguido do nome original",
            example = "NPS pós-checkout — App Transacional",
            maxLength = 120,
            nullable = true)
        String name) {

  public static DuplicateSurveyRequestDTO empty() {
    return new DuplicateSurveyRequestDTO(null, null);
  }

  public DuplicateSurveyUseCase.Input toInput(String applicationId, String surveyId) {
    return new DuplicateSurveyUseCase.Input(
        applicationId,
        surveyId,
        Optional.ofNullable(targetApplicationId).filter(value -> !value.isBlank()),
        Optional.ofNullable(name));
  }
}
