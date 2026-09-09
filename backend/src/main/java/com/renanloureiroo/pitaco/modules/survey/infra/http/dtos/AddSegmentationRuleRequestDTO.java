package com.renanloureiroo.pitaco.modules.survey.infra.http.dtos;

import com.renanloureiroo.pitaco.modules.survey.application.usecases.AddSegmentationRuleUseCase;
import com.renanloureiroo.pitaco.core.catalog.RuleOperation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import java.util.Optional;

@Schema(
    description =
        "Regra que restringe quem vê a pesquisa. value é exigido em equals e not_equals, e "
            + "recusado em present e absent")
public record AddSegmentationRuleRequestDTO(
    @NotBlank(message = "Atributo é obrigatório")
        @Size(max = 80, message = "Atributo não pode passar de 80 caracteres")
        @Schema(
            description = "Atributo do público avaliado pela regra",
            example = "plan",
            maxLength = 80,
            requiredMode = Schema.RequiredMode.REQUIRED)
        String attribute,
    @NotBlank(message = "Operação é obrigatória")
        @Pattern(
            regexp = "equals|not_equals|present|absent",
            message = "Operação deve ser equals, not_equals, present ou absent")
        @Schema(
            description = "Comparação aplicada ao atributo",
            allowableValues = {"equals", "not_equals", "present", "absent"},
            example = "equals",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String operation,
    @Size(max = 200, message = "Valor não pode passar de 200 caracteres")
        @Schema(
            description = "Valor de comparação; ausente em present e absent",
            example = "pro",
            maxLength = 200,
            nullable = true)
        String value) {

  public AddSegmentationRuleUseCase.Input toInput(String applicationId, String surveyId) {
    return new AddSegmentationRuleUseCase.Input(
        applicationId,
        surveyId,
        attribute,
        RuleOperation.valueOf(operation.toUpperCase(Locale.ROOT)),
        Optional.ofNullable(value));
  }
}
