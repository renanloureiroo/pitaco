package com.renanloureiroo.pitaco.modules.app.infra.http.dtos;

import com.renanloureiroo.pitaco.modules.app.application.usecases.ListApiKeysUseCase;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApiKeyStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.Locale;
import java.util.Optional;

@Schema(description = "Filtro e recorte de página da listagem de chaves")
public record ListApiKeysQueryDTO(
    @Pattern(regexp = "active|revoked", message = "Estado deve ser active ou revoked")
        @Schema(
            description =
                "Restringe o resultado a um estado. Ausente devolve todas as chaves, válidas "
                    + "e revogadas",
            allowableValues = {"active", "revoked"},
            example = "active")
        String status,
    @Min(value = 0, message = "Página não pode ser negativa")
        @Schema(description = "Página desejada, começando em 0", defaultValue = "0", example = "0")
        Integer page,
    @Min(value = 1, message = "Tamanho de página deve estar entre 1 e 100")
        @Max(value = 100, message = "Tamanho de página deve estar entre 1 e 100")
        @Schema(
            description = "Quantidade de chaves por página",
            defaultValue = "20",
            example = "20")
        Integer size) {

  private static final int DEFAULT_PAGE = 0;
  private static final int DEFAULT_SIZE = 20;

  public ListApiKeysUseCase.Input toInput(String applicationId) {
    return new ListApiKeysUseCase.Input(
        applicationId,
        Optional.ofNullable(status)
            .map(value -> ApiKeyStatus.valueOf(value.toUpperCase(Locale.ROOT))),
        page == null ? DEFAULT_PAGE : page,
        size == null ? DEFAULT_SIZE : size);
  }
}
