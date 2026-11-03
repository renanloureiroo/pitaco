package com.renanloureiroo.pitaco.modules.privacy.infra.http.dtos;

import com.renanloureiroo.pitaco.modules.privacy.application.usecases.ListDeletionAuditsUseCase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "Recorte de página da listagem de exclusões")
public record ListDeletionAuditsQueryDTO(
    @Min(value = 0, message = "Página não pode ser negativa")
        @Schema(description = "Página desejada, começando em 0", defaultValue = "0", example = "0")
        Integer page,
    @Min(value = 1, message = "Tamanho de página deve estar entre 1 e 100")
        @Max(value = 100, message = "Tamanho de página deve estar entre 1 e 100")
        @Schema(description = "Registros por página", defaultValue = "20", example = "20")
        Integer size) {

  private static final int DEFAULT_PAGE = 0;
  private static final int DEFAULT_SIZE = 20;

  public ListDeletionAuditsUseCase.Input toInput(String applicationId) {
    return new ListDeletionAuditsUseCase.Input(
        applicationId, page == null ? DEFAULT_PAGE : page, size == null ? DEFAULT_SIZE : size);
  }
}
