package com.renanloureiroo.pitaco.modules.collect.infra.http.dtos;

import com.renanloureiroo.pitaco.modules.collect.application.outputs.AnswerReadStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(
    description =
        "Uma resposta da exibição. O campo preenchido é o que o tipo da pergunta determina, e "
            + "nenhum vem preenchido quando a pergunta foi pulada ou o texto já expirou")
public record AnswerReadResponseDTO(
    @Schema(
            description = "Chave estável da pergunta, a mesma que atravessa as versões",
            example = "3f9a1c72-5d84-4a1e-9b0f-2c6e8d5a7b31",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String questionKey,
    @Schema(
            description =
                "ANSWERED quando respondida, SKIPPED quando a pessoa pulou, NOT_APPLICABLE quando "
                    + "a condição de exibição tirou a pergunta do caminho, EXPIRED quando era "
                    + "texto livre e o prazo de retenção da aplicação já venceu",
            allowableValues = {"ANSWERED", "SKIPPED", "NOT_APPLICABLE", "EXPIRED"},
            example = "ANSWERED",
            requiredMode = Schema.RequiredMode.REQUIRED)
        AnswerReadStatus status,
    @Schema(
            description = "Texto respondido, nas perguntas de texto livre",
            example = "o relatório podia exportar em CSV",
            nullable = true)
        String text,
    @Schema(description = "Valor respondido, nas perguntas numéricas", example = "9", nullable = true)
        Integer number,
    @Schema(
            description = "Opções escolhidas, nas perguntas de escolha. Vazio nos demais tipos",
            requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> options) {}
