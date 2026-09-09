package com.renanloureiroo.pitaco.modules.app.infra.http.presenters;

import com.renanloureiroo.pitaco.modules.app.application.usecases.GetApplicationUseCase;
import com.renanloureiroo.pitaco.modules.app.infra.http.dtos.ApplicationResponseDTO;
import java.util.Locale;

public final class GetApplicationPresenter {

  private GetApplicationPresenter() {}

  public static ApplicationResponseDTO present(GetApplicationUseCase.Output output) {
    return new ApplicationResponseDTO(
        output.id(),
        output.slug(),
        output.name(),
        output.status().name().toLowerCase(Locale.ROOT),
        output.quietPeriodDays().orElse(null),
        output.retentionDays().orElse(null),
        output.openTextRetentionDays().orElse(null),
        output.createdAt(),
        output.updatedAt());
  }
}
