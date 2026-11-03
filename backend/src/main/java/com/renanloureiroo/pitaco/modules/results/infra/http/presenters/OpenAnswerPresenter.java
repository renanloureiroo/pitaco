package com.renanloureiroo.pitaco.modules.results.infra.http.presenters;

import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.modules.results.application.outputs.OpenAnswerOutput;
import com.renanloureiroo.pitaco.modules.results.application.usecases.ListOpenAnswersUseCase;
import com.renanloureiroo.pitaco.modules.results.infra.http.dtos.OpenAnswerResponseDTO;

public final class OpenAnswerPresenter {

  private OpenAnswerPresenter() {}

  public static PageResponseDTO<OpenAnswerResponseDTO> present(ListOpenAnswersUseCase.Output output) {
    return new PageResponseDTO<>(
        output.items().stream().map(OpenAnswerPresenter::present).toList(),
        output.page(),
        output.size(),
        output.total(),
        output.totalPages());
  }

  public static OpenAnswerResponseDTO present(OpenAnswerOutput output) {
    return new OpenAnswerResponseDTO(
        output.displayId(),
        output.questionKey().value(),
        output.statement(),
        output.text(),
        output.answeredAt(),
        output.context().stream()
            .map(
                context ->
                    new OpenAnswerResponseDTO.AnswerContextDTO(
                        context.questionKey().value(), context.statement(), context.value()))
            .toList());
  }
}
