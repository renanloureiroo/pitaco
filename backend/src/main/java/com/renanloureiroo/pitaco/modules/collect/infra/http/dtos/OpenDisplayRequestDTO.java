package com.renanloureiroo.pitaco.modules.collect.infra.http.dtos;

import com.renanloureiroo.pitaco.modules.collect.application.usecases.OpenSurveyDisplayUseCase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.Optional;

@Schema(description = "Abre a exibição de exibição de uma versão publicada")
public record OpenDisplayRequestDTO(
    @NotBlank(message = "Identificador de exibição é obrigatório")
        @Pattern(
            regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
            message = "Identificador de exibição deve ser um UUID")
        @Schema(
            description = "Gerado no dispositivo; é a chave de idempotência do reenvio",
            example = "3b1f0a2c-6c9a-4a1e-9d0b-2c1f7a3e5d90",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String displayId,
    @NotBlank(message = "Identificador de pesquisa é obrigatório")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String surveyId,
    @NotBlank(message = "Identificador de versão é obrigatório")
        @Schema(
            description = "A versão exibida, que fica congelada na exibição",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String versionId,
    @Valid
        @NotNull(message = "Identificação do respondente é obrigatória")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        RespondentDTO respondent,
    @Size(max = 50, message = "No máximo 50 atributos por consulta")
        @Schema(description = "Instantâneo dos atributos no momento da exibição")
        Map<
                @Size(max = 80, message = "Nome do atributo não pode passar de 80 caracteres")
                String,
                @Size(max = 200, message = "Valor do atributo não pode passar de 200 caracteres")
                String>
            attributes,
    @Size(max = 40, message = "Versão do SDK não pode passar de 40 caracteres")
        @Schema(example = "1.4.2", maxLength = 40) String sdkVersion) {

  public OpenSurveyDisplayUseCase.Input toInput(String applicationId) {
    return new OpenSurveyDisplayUseCase.Input(
        applicationId,
        displayId,
        surveyId,
        versionId,
        Optional.ofNullable(respondent).flatMap(RespondentDTO::referenceOrEmpty),
        Optional.ofNullable(respondent).flatMap(RespondentDTO::deviceIdOrEmpty),
        attributes == null ? Map.of() : attributes,
        Optional.ofNullable(sdkVersion));
  }
}
