package com.renanloureiroo.pitaco.modules.collect.infra.http.dtos;

import com.renanloureiroo.pitaco.modules.collect.application.usecases.ListSdkErrorsUseCase;
import com.renanloureiroo.pitaco.modules.collect.domain.health.SdkErrorKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Optional;

@Schema(description = "Filtros e página da listagem de erros do SDK")
public record ListSdkErrorsQueryDTO(
    @Min(value = 0, message = "Página não pode ser negativa")
        @Schema(description = "Página desejada, começando em 0", defaultValue = "0", example = "0")
        Integer page,
    @Min(value = 1, message = "Tamanho de página deve estar entre 1 e 100")
        @Max(value = 100, message = "Tamanho de página deve estar entre 1 e 100")
        @Schema(description = "Relatórios por página", defaultValue = "20", example = "20")
        Integer size,
    @Pattern(
            regexp = "^(render_error|network_error|malformed_response|storage_error|unknown)$",
            message =
                "Tipo deve ser render_error, network_error, malformed_response, storage_error ou "
                    + "unknown")
        @Schema(
            description = "Restringe a um tipo de falha",
            allowableValues = {
              "render_error",
              "network_error",
              "malformed_response",
              "storage_error",
              "unknown"
            })
        String kind,
    @Size(max = 40, message = "Versão do SDK não pode passar de 40 caracteres")
        @Schema(description = "Restringe a uma versão do SDK, exata", example = "1.4.2")
        String sdkVersion) {

  private static final int DEFAULT_PAGE = 0;
  private static final int DEFAULT_SIZE = 20;

  public ListSdkErrorsUseCase.Input toInput(String applicationId) {
    return new ListSdkErrorsUseCase.Input(
        applicationId,
        Optional.ofNullable(kind).map(SdkErrorKind::fromWire),
        Optional.ofNullable(sdkVersion).filter(value -> !value.isBlank()),
        page == null ? DEFAULT_PAGE : page,
        size == null ? DEFAULT_SIZE : size);
  }
}
