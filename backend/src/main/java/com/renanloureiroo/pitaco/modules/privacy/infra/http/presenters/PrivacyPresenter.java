package com.renanloureiroo.pitaco.modules.privacy.infra.http.presenters;

import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.modules.privacy.application.outputs.DeletionAuditOutput;
import com.renanloureiroo.pitaco.modules.privacy.application.outputs.RespondentErasureOutput;
import com.renanloureiroo.pitaco.modules.privacy.application.outputs.RetentionPreviewOutput;
import com.renanloureiroo.pitaco.modules.privacy.application.usecases.ListDeletionAuditsUseCase;
import com.renanloureiroo.pitaco.modules.privacy.infra.http.dtos.DeletionAuditResponseDTO;
import com.renanloureiroo.pitaco.modules.privacy.infra.http.dtos.RespondentErasureResponseDTO;
import com.renanloureiroo.pitaco.modules.privacy.infra.http.dtos.RetentionPreviewResponseDTO;

public final class PrivacyPresenter {

  private PrivacyPresenter() {}

  public static RespondentErasureResponseDTO present(RespondentErasureOutput output) {
    return new RespondentErasureResponseDTO(
        output.deleted(), output.displaysDeleted(), output.answersDeleted());
  }

  public static PageResponseDTO<DeletionAuditResponseDTO> present(
      ListDeletionAuditsUseCase.Output output) {
    return new PageResponseDTO<>(
        output.items().stream().map(PrivacyPresenter::present).toList(),
        output.page(),
        output.size(),
        output.total(),
        output.totalPages());
  }

  public static DeletionAuditResponseDTO present(DeletionAuditOutput output) {
    return new DeletionAuditResponseDTO(
        output.id(),
        output.displaysDeleted(),
        output.answersDeleted(),
        output.performedBy().orElse(null),
        output.performedAt());
  }

  public static RetentionPreviewResponseDTO present(RetentionPreviewOutput output) {
    return new RetentionPreviewResponseDTO(
        output.configured(),
        output.answerRetentionDays().orElse(null),
        output.textRetentionDays().orElse(null),
        output.nextRunAt().orElse(null),
        forecastOf(output.nextRun()),
        forecastOf(output.nextWeek()),
        output.lastRunAt().orElse(null),
        output.firstDiscardPending());
  }

  private static RetentionPreviewResponseDTO.ForecastDTO forecastOf(
      RetentionPreviewOutput.Forecast forecast) {
    return new RetentionPreviewResponseDTO.ForecastDTO(forecast.answers(), forecast.texts());
  }
}
