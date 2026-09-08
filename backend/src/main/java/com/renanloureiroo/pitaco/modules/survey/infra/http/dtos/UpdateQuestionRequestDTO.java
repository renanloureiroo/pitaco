package com.renanloureiroo.pitaco.modules.survey.infra.http.dtos;

import com.renanloureiroo.pitaco.modules.survey.application.usecases.UpdateQuestionUseCase;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(
    description =
        "Novo conteúdo de uma pergunta. A chave estável e a posição não mudam: reescrever é a "
            + "mesma pergunta, com outro texto")
public record UpdateQuestionRequestDTO(
    @NotBlank(message = "Enunciado é obrigatório")
        @Size(max = 500, message = "Enunciado não pode passar de 500 caracteres")
        @Schema(
            description = "Texto da pergunta",
            example = "O que achou do atendimento?",
            maxLength = 500,
            requiredMode = Schema.RequiredMode.REQUIRED)
        String statement,
    @NotBlank(message = "Tipo é obrigatório")
        @Pattern(
            regexp = "single_choice|multiple_choice|rating|scale|nps|free_text",
            message =
                "Tipo deve ser single_choice, multiple_choice, rating, scale, nps ou free_text")
        @Schema(
            description = "Tipo da pergunta",
            allowableValues = {
              "single_choice",
              "multiple_choice",
              "rating",
              "scale",
              "nps",
              "free_text"
            },
            example = "single_choice",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String type,
    @NotNull(message = "Obrigatoriedade é obrigatória")
        @Schema(
            description = "Se a resposta é obrigatória",
            example = "true",
            requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean required,
    @Valid @Schema(description = "Alternativas, apenas nos tipos de escolha")
        List<QuestionOptionDTO> options,
    @Valid @Schema(description = "Faixa numérica, apenas nos tipos que a exigem", nullable = true)
        ScaleRangeDTO range) {

  public UpdateQuestionUseCase.Input toInput(
      String applicationId, String surveyId, String questionId) {
    return new UpdateQuestionUseCase.Input(
        applicationId,
        surveyId,
        questionId,
        new AddQuestionRequestDTO(statement, type, required, options, range).toDraft());
  }
}
