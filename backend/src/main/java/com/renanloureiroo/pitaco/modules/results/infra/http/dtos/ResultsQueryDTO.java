package com.renanloureiroo.pitaco.modules.results.infra.http.dtos;

import com.renanloureiroo.pitaco.modules.results.application.usecases.ExportSurveyResultsUseCase;
import com.renanloureiroo.pitaco.modules.results.application.usecases.GetSurveyResultsUseCase;
import com.renanloureiroo.pitaco.modules.results.application.usecases.ResultsSelection;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Optional;

@Schema(description = "Recorte aplicado à leitura de resultados")
public record ResultsQueryDTO(
    @Schema(
            description = "Limite inferior da abertura das exibições, em UTC e inclusive",
            example = "2026-09-01T00:00:00Z")
        Instant from,
    @Schema(
            description = "Limite superior da abertura das exibições, em UTC e inclusive",
            example = "2026-09-30T23:59:59Z")
        Instant to,
    @Size(max = 80, message = "Nome do atributo não pode passar de 80 caracteres")
        @Schema(
            description =
                "Nome do atributo do respondente para o recorte. Sozinho, sem attributeValue, "
                    + "seleciona as exibições em que o atributo está ausente",
            example = "plano")
        String attribute,
    @Size(max = 200, message = "Valor do atributo não pode passar de 200 caracteres")
        @Schema(
            description = "Valor exigido do atributo. Só faz sentido junto de attribute",
            example = "pro")
        String attributeValue,
    @Min(value = 1, message = "Número de versão deve ser maior ou igual a 1")
        @Schema(
            description = "Restringe a uma versão pelo número. Ausente consolida todas",
            example = "2")
        Integer version) {

  @AssertTrue(message = "Início do período não pode ser posterior ao fim")
  @Schema(hidden = true)
  public boolean isPeriodOrdered() {
    return from == null || to == null || !from.isAfter(to);
  }

  @AssertTrue(message = "Valor do atributo exige o nome do atributo")
  @Schema(hidden = true)
  public boolean isAttributeValueScoped() {
    return attributeValue == null || (attribute != null && !attribute.isBlank());
  }

  @AssertTrue(message = "Nome do atributo não pode ser vazio")
  @Schema(hidden = true)
  public boolean isAttributeNamed() {
    return attribute == null || !attribute.isBlank();
  }

  public ResultsSelection toSelection() {
    return new ResultsSelection(
        Optional.ofNullable(from),
        Optional.ofNullable(to),
        Optional.ofNullable(attribute),
        Optional.ofNullable(attributeValue),
        Optional.ofNullable(version));
  }

  public GetSurveyResultsUseCase.Input toInput(String applicationId, String surveyId) {
    return new GetSurveyResultsUseCase.Input(applicationId, surveyId, toSelection());
  }

  public ExportSurveyResultsUseCase.Input toExportInput(String applicationId, String surveyId) {
    return new ExportSurveyResultsUseCase.Input(applicationId, surveyId, toSelection());
  }
}
