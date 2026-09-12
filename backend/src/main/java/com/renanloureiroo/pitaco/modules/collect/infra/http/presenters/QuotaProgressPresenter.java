package com.renanloureiroo.pitaco.modules.collect.infra.http.presenters;

import com.renanloureiroo.pitaco.modules.collect.application.usecases.GetQuotaProgressUseCase;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.QuotaProgressResponseDTO;

public final class QuotaProgressPresenter {

  private QuotaProgressPresenter() {}

  public static QuotaProgressResponseDTO present(GetQuotaProgressUseCase.Output output) {
    return new QuotaProgressResponseDTO(
        output.responseQuota().orElse(null), output.completedResponses());
  }
}
