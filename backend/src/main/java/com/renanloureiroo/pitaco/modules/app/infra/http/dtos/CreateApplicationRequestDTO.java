package com.renanloureiroo.pitaco.modules.app.infra.http.dtos;

import com.renanloureiroo.pitaco.modules.app.application.usecases.CreateApplicationUseCase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados para criar uma aplicação")
public record CreateApplicationRequestDTO(
    @NotBlank(message = "Nome é obrigatório")
        @Size(max = 120, message = "Nome não pode passar de 120 caracteres")
        @Schema(
            description = "Nome de exibição da aplicação",
            example = "Acme App",
            maxLength = 120,
            requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
    @Size(max = 50, message = "Slug não pode passar de 50 caracteres")
        @Pattern(
            regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
            message = "Slug aceita apenas minúsculas, dígitos e hífen entre termos")
        @Schema(
            description = "Identificador legível. Quando omitido, é derivado do nome",
            example = "acme-app",
            maxLength = 50,
            pattern = "^[a-z0-9]+(-[a-z0-9]+)*$",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String slug,
    @Positive(message = "O intervalo de descanso deve ser de ao menos um dia")
        @Schema(
            description =
                "Dias de descanso exigidos entre pesquisas do mesmo respondente. "
                    + "Omitido, a aplicação não impõe descanso",
            example = "15",
            minimum = "1",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Integer quietPeriodDays,
    @Positive(message = "O prazo de retenção deve ser de ao menos um dia")
        @Schema(
            description = "Dias até o descarte das respostas. Omitido, nada é descartado por tempo",
            example = "180",
            minimum = "1",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Integer retentionDays,
    @Positive(message = "O prazo de retenção de texto livre deve ser de ao menos um dia")
        @Schema(
            description =
                "Dias até o descarte do texto livre, nunca maior que o prazo geral. "
                    + "Omitido, o texto livre segue o prazo geral",
            example = "30",
            minimum = "1",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Integer openTextRetentionDays) {

  public CreateApplicationUseCase.Input toInput() {
    return new CreateApplicationUseCase.Input(
        name, slug, quietPeriodDays, retentionDays, openTextRetentionDays);
  }
}
