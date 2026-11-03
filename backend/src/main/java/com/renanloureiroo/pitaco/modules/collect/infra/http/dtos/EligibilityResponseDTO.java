package com.renanloureiroo.pitaco.modules.collect.infra.http.dtos;

import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

// Envelope, e não 204: ausência é sucesso, e o SDK desserializa uma forma só (D-15).
@Schema(description = "A pesquisa a exibir agora, ou nulo quando não há nenhuma")
public record EligibilityResponseDTO(
    @Schema(
            description = "A versão publicada inteira, ou nulo quando não há pesquisa a exibir",
            nullable = true)
        SurveyDTO survey) {

  @Schema(name = "DeliverableSurvey", description = "Versão publicada pronta para exibição")
  public record SurveyDTO(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String surveyId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String versionId,
      @Schema(example = "3", requiredMode = Schema.RequiredMode.REQUIRED) int versionNumber,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<QuestionDTO> questions,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) FreeTextNoticeDTO freeTextNotice) {}

  @Schema(
      name = "DeliverableFreeTextNotice",
      description =
          "Aviso curto para o SDK mostrar junto dos campos de texto livre, pedindo que o "
              + "respondente não escreva dado pessoal. Desligado, o SDK não mostra nada")
  public record FreeTextNoticeDTO(
      @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED) boolean enabled,
      @Schema(
              description = "Texto já resolvido; ausente quando o aviso está desligado",
              example = "Evite escrever dados pessoais, como nome, telefone ou e-mail.",
              nullable = true)
          String text) {}

  @Schema(name = "DeliverableQuestion", description = "Pergunta na ordem definida pela autoria")
  public record QuestionDTO(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String key,
      @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) int position,
      @Schema(example = "O que achou do checkout?", requiredMode = Schema.RequiredMode.REQUIRED)
          String statement,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) QuestionType type,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean required,
      @Schema(description = "Vazia nos tipos que não aceitam opções") List<OptionDTO> options,
      @Schema(description = "Presente apenas nos tipos numéricos", nullable = true)
          RangeDTO range,
      @Schema(
              description =
                  "Presente quando a pergunta só aparece se a resposta a uma anterior satisfaz a "
                      + "condição. O SDK avalia localmente; pulada pela condição, a pergunta é "
                      + "enviada com status NOT_APPLICABLE",
              nullable = true)
          ConditionDTO condition) {}

  @Schema(name = "DeliverableOption")
  public record OptionDTO(String label, String value, int position) {}

  @Schema(name = "DeliverableRange")
  public record RangeDTO(
      int min,
      int max,
      @Schema(description = "Texto junto do menor valor", nullable = true) String minLabel,
      @Schema(description = "Texto junto do maior valor", nullable = true) String maxLabel) {}

  @Schema(
      name = "DeliverableCondition",
      description =
          "equals e not_equals comparam com um valor, in com um conjunto, between com a faixa "
              + "inclusiva [min, max]. Em múltipla escolha, equals significa \"contém\"")
  public record ConditionDTO(
      @Schema(description = "Chave da pergunta anterior de onde vem a resposta") String sourceKey,
      @Schema(allowableValues = {"equals", "not_equals", "in", "between"}) String operator,
      @Schema(description = "Valores de opção, ou números como texto nas escalas") List<String> values,
      @Schema(nullable = true) Integer min,
      @Schema(nullable = true) Integer max) {}
}
