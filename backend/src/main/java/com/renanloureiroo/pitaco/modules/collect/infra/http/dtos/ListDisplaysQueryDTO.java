package com.renanloureiroo.pitaco.modules.collect.infra.http.dtos;

import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayOutcome;
import com.renanloureiroo.pitaco.modules.collect.application.usecases.ListRespondentDisplaysUseCase;
import com.renanloureiroo.pitaco.modules.collect.application.usecases.ListSurveyDisplaysUseCase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.Optional;

@Schema(description = "Filtro e recorte de página da listagem de exibições")
public record ListDisplaysQueryDTO(
    @Min(value = 1, message = "Número de versão deve ser maior ou igual a 1")
        @Schema(
            description =
                "Restringe às exibições de uma versão, pelo número — que é o que identifica a "
                    + "versão no painel. Ausente devolve as de todas as versões",
            example = "3")
        Integer versionNumber,
    @Schema(
            description = "Restringe a um desfecho. Ausente devolve todos",
            example = "COMPLETED")
        DisplayOutcomeFilter outcome,
    @Schema(
            description = "Limite inferior da abertura, em UTC e inclusive",
            example = "2026-09-08T00:00:00Z")
        Instant openedFrom,
    @Schema(
            description = "Limite superior da abertura, em UTC e inclusive",
            example = "2026-09-08T23:59:59Z")
        Instant openedTo,
    @Min(value = 0, message = "Página não pode ser negativa")
        @Schema(description = "Página desejada, começando em 0", defaultValue = "0", example = "0")
        Integer page,
    @Min(value = 1, message = "Tamanho de página deve estar entre 1 e 100")
        @Max(value = 100, message = "Tamanho de página deve estar entre 1 e 100")
        @Schema(
            description = "Quantidade de exibições por página",
            defaultValue = "20",
            example = "20")
        Integer size) {

  private static final int DEFAULT_PAGE = 0;
  private static final int DEFAULT_SIZE = 20;

  // Restrição de consulta, não invariante de domínio: por isso mora aqui e recusa 400 apontando
  // o campo, em vez de virar value object e sair como 422 (D-12).
  @AssertTrue(message = "Início do período não pode ser posterior ao fim")
  @Schema(hidden = true)
  public boolean isPeriodOrdered() {
    return openedFrom == null || openedTo == null || !openedFrom.isAfter(openedTo);
  }

  public ListSurveyDisplaysUseCase.Input toInput(String applicationId, String surveyId) {
    return new ListSurveyDisplaysUseCase.Input(
        applicationId,
        surveyId,
        Optional.ofNullable(versionNumber),
        outcomeAsDomain(),
        Optional.ofNullable(openedFrom),
        Optional.ofNullable(openedTo),
        pageOrDefault(),
        sizeOrDefault());
  }

  // A listagem por respondente reaproveita este DTO e ignora versionNumber (FR-025).
  public ListRespondentDisplaysUseCase.Input toRespondentInput(
      String applicationId, String respondentId) {
    return new ListRespondentDisplaysUseCase.Input(
        applicationId,
        respondentId,
        outcomeAsDomain(),
        Optional.ofNullable(openedFrom),
        Optional.ofNullable(openedTo),
        pageOrDefault(),
        sizeOrDefault());
  }

  private Optional<DisplayOutcome> outcomeAsDomain() {
    return Optional.ofNullable(outcome).map(DisplayOutcomeFilter::toDomain);
  }

  private int pageOrDefault() {
    return page == null ? DEFAULT_PAGE : page;
  }

  private int sizeOrDefault() {
    return size == null ? DEFAULT_SIZE : size;
  }
}
