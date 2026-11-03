package com.renanloureiroo.pitaco.modules.survey.infra.http.presenters;

import com.renanloureiroo.pitaco.modules.survey.application.outputs.SurveyOutput;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.FreeTextNotice;
import java.util.Locale;

// Criar, editar, pausar, retomar e encerrar devolvem a mesma coisa, então traduzem pelo mesmo
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
        output.priority(),
        output.responseQuota().orElse(null),
        output.ignoresQuietPeriod(),
        output.template().map(template -> template.name().toLowerCase(Locale.ROOT)).orElse(null),
        noticeOf(output.freeTextNotice()),
        output.createdAt());
  }

  private static SurveyResponseDTO.FreeTextNoticeDTO noticeOf(FreeTextNotice notice) {
    return new SurveyResponseDTO.FreeTextNoticeDTO(
        notice.enabled(),
        notice.customText().orElse(null),
        notice.text(),
        FreeTextNotice.DEFAULT_TEXT);
  }
}
