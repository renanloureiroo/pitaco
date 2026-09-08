package com.renanloureiroo.pitaco.modules.survey.infra.http.dtos;

import com.renanloureiroo.pitaco.modules.survey.application.usecases.ReorderQuestionsUseCase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Schema(
    description =
        "Permutação exata dos identificadores das perguntas da versão, na nova ordem. A "
            + "integridade é verificada no resultado final, não a cada passo")
public record ReorderQuestionsRequestDTO(
    @NotEmpty(message = "A nova ordem é obrigatória")
        @Schema(
            description = "Identificadores das perguntas, na ordem desejada",
            requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> questionIds) {

  public ReorderQuestionsUseCase.Input toInput(String applicationId, String surveyId) {
    return new ReorderQuestionsUseCase.Input(
        applicationId, surveyId, questionIds == null ? List.of() : questionIds);
  }
}
