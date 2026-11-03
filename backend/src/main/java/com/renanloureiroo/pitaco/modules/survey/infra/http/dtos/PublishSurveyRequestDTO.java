package com.renanloureiroo.pitaco.modules.survey.infra.http.dtos;

import com.renanloureiroo.pitaco.modules.survey.application.usecases.PublishSurveyUseCase;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.ChangeKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import java.util.Optional;

@Schema(
    description =
        "Classificação da mudança. Obrigatória a partir da versão 2; ignorada na versão 1. "
            + "cosmetic é verificada contra a versão anterior e recusada quando há pergunta "
            + "acrescentada, removida, retipada ou com o conjunto de opções alterado")
public record PublishSurveyRequestDTO(
    @Pattern(regexp = "cosmetic|semantic", message = "Classificação deve ser cosmetic ou semantic")
        @Schema(
            description = "Natureza da mudança em relação à versão publicada anterior",
            allowableValues = {"cosmetic", "semantic"},
            example = "cosmetic",
            nullable = true)
        String changeKind,
    @Size(max = 500, message = "Resumo da mudança não pode passar de 500 caracteres")
        @Schema(
            description = "Descrição textual do que mudou",
            example = "Correção de acentuação no enunciado 3",
            maxLength = 500,
            nullable = true)
        String changeSummary) {

  public PublishSurveyUseCase.Input toInput(String applicationId, String surveyId) {
    return new PublishSurveyUseCase.Input(
        applicationId,
        surveyId,
        Optional.ofNullable(changeKind)
            .map(kind -> ChangeKind.valueOf(kind.toUpperCase(Locale.ROOT))),
        Optional.ofNullable(changeSummary));
  }

  public static PublishSurveyRequestDTO empty() {
    return new PublishSurveyRequestDTO(null, null);
  }
}
