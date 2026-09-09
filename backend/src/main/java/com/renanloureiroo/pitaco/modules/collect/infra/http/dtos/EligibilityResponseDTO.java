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
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<QuestionDTO> questions) {}

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
          RangeDTO range) {}

  @Schema(name = "DeliverableOption")
  public record OptionDTO(String label, String value, int position) {}

  @Schema(name = "DeliverableRange")
  public record RangeDTO(int min, int max) {}
}
