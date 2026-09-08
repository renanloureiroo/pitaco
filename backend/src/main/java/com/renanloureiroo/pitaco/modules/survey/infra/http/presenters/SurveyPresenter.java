package com.renanloureiroo.pitaco.modules.survey.infra.http.presenters;

import com.renanloureiroo.pitaco.modules.survey.application.outputs.SurveyOutput;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyResponseDTO;
import java.util.Locale;

// Criar, renomear, pausar, retomar e encerrar devolvem a mesma coisa, então traduzem pelo mesmo
// presenter.
public final class SurveyPresenter {

  private SurveyPresenter() {}

  public static SurveyResponseDTO present(SurveyOutput output) {
    return new SurveyResponseDTO(
        output.id(),
        output.applicationId(),
        output.name(),
        output.state().name().toLowerCase(Locale.ROOT),
        output.publishedVersionNumber().orElse(null),
        output.draftVersionNumber().orElse(null),
        output.createdAt());
  }
}
