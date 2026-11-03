package com.renanloureiroo.pitaco.modules.survey.infra.http.dtos;

import com.renanloureiroo.pitaco.modules.survey.application.usecases.CreateSurveyUseCase;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SurveyTemplate;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import java.util.Optional;

@Schema(description = "Dados para criar uma pesquisa em rascunho")
public record CreateSurveyRequestDTO(
    @NotBlank(message = "Nome é obrigatório")
        @Size(max = 120, message = "Nome não pode passar de 120 caracteres")
        @Schema(
            description = "Nome pelo qual a pesquisa é reconhecida na aplicação",
            example = "NPS pós-checkout",
            maxLength = 120,
            requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
    @Pattern(regexp = "nps|csat|ces", message = "Modelo deve ser nps, csat ou ces")
        @Schema(
            description =
                "Modelo pronto de onde partir. Com ele, o rascunho nasce com a pergunta do "
                    + "formato, editável; sem ele, nasce vazio",
            allowableValues = {"nps", "csat", "ces"},
            example = "nps",
            nullable = true)
        String template) {

  public CreateSurveyRequestDTO(String name) {
    this(name, null);
  }

  public CreateSurveyUseCase.Input toInput(String applicationId) {
    return new CreateSurveyUseCase.Input(
        applicationId,
        name,
        Optional.ofNullable(template)
            .map(value -> SurveyTemplate.valueOf(value.toUpperCase(Locale.ROOT))));
  }
}
