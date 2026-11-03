package com.renanloureiroo.pitaco.modules.survey.infra.http.dtos;

import com.renanloureiroo.pitaco.modules.survey.application.services.QuestionDrafts;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.ConditionOperator;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Schema(
    description =
        "Exibe a pergunta só quando a resposta a uma pergunta anterior satisfaz a condição. Em "
            + "múltipla escolha, equals significa que a resposta contém o valor")
public record ConditionDTO(
    @NotBlank(message = "Pergunta de origem é obrigatória")
        @Schema(
            description = "Chave estável de uma pergunta anterior, que não seja de texto livre",
            example = "b7c9a1d2-3e45-4f67-8901-2a3b4c5d6e7f",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String sourceKey,
    @NotBlank(message = "Operador é obrigatório")
        @Pattern(
            regexp = "equals|not_equals|in|between",
            message = "Operador deve ser equals, not_equals, in ou between")
        @Schema(
            description = "between só em perguntas de escala",
            allowableValues = {"equals", "not_equals", "in", "between"},
            example = "between",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String operator,
    @Schema(
            description =
                "Valores comparados: um em equals e not_equals, de um a cinquenta em in, nenhum "
                    + "em between. Nas perguntas de escala, números escritos como texto")
        List<String> values,
    @Schema(description = "Mínimo da faixa, inclusivo; só em between", example = "0", nullable = true)
        Integer min,
    @Schema(description = "Máximo da faixa, inclusivo; só em between", example = "6", nullable = true)
        Integer max) {

  QuestionDrafts.ConditionInput toInput() {
    return new QuestionDrafts.ConditionInput(
        sourceKey,
        ConditionOperator.valueOf(operator.toUpperCase(Locale.ROOT)),
        values == null ? List.of() : values,
        Optional.ofNullable(min),
        Optional.ofNullable(max));
  }
}
