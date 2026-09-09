package com.renanloureiroo.pitaco.modules.collect.infra.http.presenters;

import com.renanloureiroo.pitaco.modules.collect.application.outputs.AnswerReadOutput;
import com.renanloureiroo.pitaco.modules.collect.application.outputs.DisplayDetailOutput;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.AnswerReadResponseDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.DisplayDetailResponseDTO;

// Corpo plano: a exibição e sua síntese são a mesma coisa para quem lê, e aninhar summary
// obrigaria o cliente a descer um nível sem ganhar informação.
public final class DisplayDetailPresenter {

  private DisplayDetailPresenter() {}

  public static DisplayDetailResponseDTO present(DisplayDetailOutput output) {
    var summary = output.summary();

    return new DisplayDetailResponseDTO(
        summary.id().value(),
        output.respondentId().value(),
        output.surveyId().value(),
        summary.versionId().value(),
        summary.versionNumber(),
        summary.comparabilityGroup(),
        summary.outcome(),
        summary.sdkVersion().orElse(null),
        summary.openedAt(),
        summary.closedAt().orElse(null),
        output.attributes(),
        output.answers().stream().map(DisplayDetailPresenter::present).toList());
  }

  private static AnswerReadResponseDTO present(AnswerReadOutput output) {
    return new AnswerReadResponseDTO(
        output.questionKey().value(),
        output.status(),
        output.text().orElse(null),
        output.number().orElse(null),
        output.options());
  }
}
