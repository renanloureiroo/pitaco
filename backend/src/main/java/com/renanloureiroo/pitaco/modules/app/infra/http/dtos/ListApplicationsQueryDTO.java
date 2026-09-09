package com.renanloureiroo.pitaco.modules.app.infra.http.dtos;

import com.renanloureiroo.pitaco.modules.app.application.usecases.ListApplicationsUseCase;
import com.renanloureiroo.pitaco.modules.app.domain.entities.Status;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.Locale;
import java.util.Optional;

@Schema(description = "Filtro e recorte de página da listagem de aplicações")
public record ListApplicationsQueryDTO(
    @Pattern(regexp = "active|inactive", message = "Estado deve ser active ou inactive")
        @Schema(
            description =
                "Restringe o resultado a um estado. Ausente devolve todas as aplicações, "
                    + "ativas e inativas",
            allowableValues = {"active", "inactive"},
            example = "active")
        String status,
    @Min(value = 0, message = "Página não pode ser negativa")
        @Schema(description = "Página desejada, começando em 0", defaultValue = "0", example = "0")
        Integer page,
    @Min(value = 1, message = "Tamanho de página deve estar entre 1 e 100")
        @Max(value = 100, message = "Tamanho de página deve estar entre 1 e 100")
        @Schema(
            description = "Quantidade de aplicações por página",
            defaultValue = "20",
            example = "20")
        Integer size) {

  private static final int DEFAULT_PAGE = 0;
  private static final int DEFAULT_SIZE = 20;

  public ListApplicationsUseCase.Input toInput() {
    return new ListApplicationsUseCase.Input(
        Optional.ofNullable(status).map(value -> Status.valueOf(value.toUpperCase(Locale.ROOT))),
        page == null ? DEFAULT_PAGE : page,
        size == null ? DEFAULT_SIZE : size);
  }
}
