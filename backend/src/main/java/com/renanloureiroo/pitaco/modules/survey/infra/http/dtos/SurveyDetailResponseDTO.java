package com.renanloureiroo.pitaco.modules.survey.infra.http.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(
    description =
        "A pesquisa com o conteúdo montado: o do rascunho quando existe, o da versão publicada "
            + "caso contrário — indicado por content.source")
public record SurveyDetailResponseDTO(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String applicationId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
    @Schema(
            allowableValues = {"draft", "scheduled", "active", "paused", "ended"},
            requiredMode = Schema.RequiredMode.REQUIRED)
        String state,
    @Schema(nullable = true) Integer publishedVersionNumber,
    @Schema(nullable = true) Integer draftVersionNumber,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
    @Schema(description = "Conteúdo montado; ausente quando não há nenhuma versão", nullable = true)
        Content content) {

  @Schema(description = "O conteúdo de uma versão da pesquisa")
  public record Content(
      @Schema(
              description = "De onde veio o conteúdo devolvido",
              allowableValues = {"draft", "published"},
              example = "draft",
              requiredMode = Schema.RequiredMode.REQUIRED)
          String source,
      @Schema(
              description = "Número da versão de onde veio",
              example = "1",
              requiredMode = Schema.RequiredMode.REQUIRED)
          int versionNumber,
      @Schema(
              description = "Perguntas na ordem de exibição",
              requiredMode = Schema.RequiredMode.REQUIRED)
          List<QuestionResponseDTO> questions,
      @Schema(description = "Disparo da versão; ausente enquanto não foi definido", nullable = true)
          TriggerResponseDTO trigger) {}
}
