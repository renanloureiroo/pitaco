package com.renanloureiroo.pitaco.modules.app.infra.http.dtos;

import com.renanloureiroo.pitaco.modules.app.application.usecases.CreateApplicationUseCase;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Aplicação recém-criada")
public record CreateApplicationResponseDTO(
    @Schema(
            description = "Identificador da aplicação",
            example = "3f9a1c72-5d84-4a1e-9b0f-2c6e8d5a7b31",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String id,
    @Schema(
            description = "Identificador legível, derivado do nome quando não informado na criação",
            example = "acme-app",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String slug) {

  public static CreateApplicationResponseDTO from(CreateApplicationUseCase.Output output) {
    return new CreateApplicationResponseDTO(output.id(), output.slug());
  }
}
