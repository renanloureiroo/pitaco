package com.renanloureiroo.pitaco.modules.survey.infra.http.dtos;

import com.renanloureiroo.pitaco.modules.survey.application.services.QuestionDrafts;
import com.renanloureiroo.pitaco.modules.survey.application.usecases.AddQuestionUseCase;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Schema(
    description =
        "Dados de uma pergunta. options só é aceito nos tipos de escolha e pode vir vazio — a "
            + "pendência vira impedimento de publicação. range é exigido em rating e scale, e em "
            + "nps só aceita 0–10")
public record AddQuestionRequestDTO(
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

  public AddQuestionUseCase.Input toInput(String applicationId, String surveyId) {
    return new AddQuestionUseCase.Input(applicationId, surveyId, toDraft());
  }

  QuestionDrafts.Draft toDraft() {
    return new QuestionDrafts.Draft(
        statement,
        QuestionType.valueOf(type.toUpperCase(Locale.ROOT)),
        Boolean.TRUE.equals(required),
        options == null
            ? List.of()
            : options.stream()
                .map(option -> new QuestionDrafts.OptionInput(option.label(), option.value()))
                .toList(),
        Optional.ofNullable(range)
            .map(value -> new QuestionDrafts.RangeInput(value.min(), value.max())));
  }
}
