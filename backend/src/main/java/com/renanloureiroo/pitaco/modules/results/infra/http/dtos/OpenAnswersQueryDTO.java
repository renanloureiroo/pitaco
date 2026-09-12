package com.renanloureiroo.pitaco.modules.results.infra.http.dtos;

import com.renanloureiroo.pitaco.modules.results.application.usecases.ListOpenAnswersUseCase;
import com.renanloureiroo.pitaco.modules.results.application.usecases.ResultsSelection;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Optional;

@Schema(description = "Recorte, busca e página da listagem de respostas abertas")
public record OpenAnswersQueryDTO(
    @Schema(description = "Limite inferior da abertura, em UTC e inclusive") Instant from,
    @Schema(description = "Limite superior da abertura, em UTC e inclusive") Instant to,
    @Size(max = 80, message = "Nome do atributo não pode passar de 80 caracteres")
        @Schema(description = "Atributo do recorte; sozinho seleciona quem não o enviou")
        String attribute,
    @Size(max = 200, message = "Valor do atributo não pode passar de 200 caracteres")
        @Schema(description = "Valor exigido do atributo")
        String attributeValue,
    @Min(value = 1, message = "Número de versão deve ser maior ou igual a 1")
        @Schema(description = "Restringe a uma versão pelo número")
        Integer version,
    @Size(max = 200, message = "Termo de busca não pode passar de 200 caracteres")
        @Schema(description = "Termo procurado dentro do texto, sem distinguir maiúsculas", example = "confuso")
        String q,
    @Min(value = 0, message = "Página não pode ser negativa")
        @Schema(description = "Página desejada, começando em 0", defaultValue = "0")
        Integer page,
    @Min(value = 1, message = "Tamanho de página deve estar entre 1 e 100")
        @Max(value = 100, message = "Tamanho de página deve estar entre 1 e 100")
        @Schema(description = "Quantidade de respostas por página", defaultValue = "20")
        Integer size) {

  private static final int DEFAULT_PAGE = 0;
  private static final int DEFAULT_SIZE = 20;

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

  public ListOpenAnswersUseCase.Input toInput(String applicationId, String surveyId) {
    return new ListOpenAnswersUseCase.Input(
        applicationId,
        surveyId,
        new ResultsSelection(
            Optional.ofNullable(from),
            Optional.ofNullable(to),
            Optional.ofNullable(attribute),
            Optional.ofNullable(attributeValue),
            Optional.ofNullable(version)),
        Optional.ofNullable(q),
        page == null ? DEFAULT_PAGE : page,
        size == null ? DEFAULT_SIZE : size);
  }
}
