package com.renanloureiroo.pitaco.modules.collect.infra.http.dtos;

import com.renanloureiroo.pitaco.modules.collect.application.usecases.SubmitSurveyDisplayUseCase;
import com.renanloureiroo.pitaco.modules.collect.domain.collection.AnswerDraft;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayOutcome;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "As respostas e o desfecho, em um ato só")
public record SubmissionRequestDTO(
    @NotNull(message = "Desfecho é obrigatório")
        @Schema(
            description = "COMPLETED exige toda obrigatória respondida; DISMISSED aceita o parcial",
            requiredMode = Schema.RequiredMode.REQUIRED)
        Outcome outcome,
    @Valid
        @NotNull(message = "Lista de respostas é obrigatória")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<AnswerDTO> answers) {

  // Enum próprio da borda: STARTED e ABANDONED não são desfechos que alguém envia, e um valor
  // fora destes dois é corpo malformado.
  public enum Outcome {
    COMPLETED,
    DISMISSED;

    DisplayOutcome toDomain() {
      return this == COMPLETED ? DisplayOutcome.COMPLETED : DisplayOutcome.DISMISSED;
    }
  }

  public SubmitSurveyDisplayUseCase.Input toInput(String applicationId, String displayId) {
    return new SubmitSurveyDisplayUseCase.Input(
        applicationId,
        displayId,
        outcome.toDomain(),
        answers == null ? List.<AnswerDraft>of() : answers.stream().map(AnswerDTO::toDraft).toList());
  }
}
