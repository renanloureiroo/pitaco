package com.renanloureiroo.pitaco.modules.results.infra.http.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "Uma resposta de texto livre com o contexto da mesma exibição")
public record OpenAnswerResponseDTO(
    String displayId,
    String questionKey,
    String statement,
    String text,
    Instant answeredAt,
    @Schema(description = "As demais respostas dadas na mesma exibição, em texto legível")
        List<AnswerContextDTO> context) {

  public record AnswerContextDTO(String questionKey, String statement, String value) {}
}
