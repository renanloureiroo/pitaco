package com.renanloureiroo.pitaco.modules.survey.infra.http.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Alternativa de resposta de uma pergunta de escolha")
public record QuestionOptionDTO(
    @NotBlank(message = "Rótulo da opção é obrigatório")
        @Size(max = 200, message = "Rótulo da opção não pode passar de 200 caracteres")
        @Schema(
            description = "Texto exibido a quem responde",
            example = "Muito satisfeito",
            maxLength = 200,
            requiredMode = Schema.RequiredMode.REQUIRED)
        String label,
    @NotBlank(message = "Valor da opção é obrigatório")
        @Size(max = 120, message = "Valor da opção não pode passar de 120 caracteres")
        @Schema(
            description = "Valor gravado na resposta; único dentro da pergunta",
            example = "very_satisfied",
            maxLength = 120,
            requiredMode = Schema.RequiredMode.REQUIRED)
        String value) {}
