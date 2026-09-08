package com.renanloureiroo.pitaco.modules.app.infra.http.dtos;

import com.renanloureiroo.pitaco.modules.app.application.usecases.IssueApiKeyUseCase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados para emitir uma chave de API")
public record IssueApiKeyRequestDTO(
    @NotBlank(message = "Rótulo é obrigatório")
        @Size(max = 80, message = "Rótulo não pode passar de 80 caracteres")
        @Schema(
            description = "Rótulo que descreve onde a chave será usada",
            example = "app iOS",
            maxLength = 80,
            requiredMode = Schema.RequiredMode.REQUIRED)
        String label) {

  public IssueApiKeyUseCase.Input toInput(String applicationId) {
    return new IssueApiKeyUseCase.Input(applicationId, label);
  }
}
