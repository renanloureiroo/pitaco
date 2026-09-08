package com.renanloureiroo.pitaco.modules.app.infra.http.presenters;

import com.renanloureiroo.pitaco.modules.app.application.usecases.IssueApiKeyUseCase;
import com.renanloureiroo.pitaco.modules.app.infra.http.dtos.IssueApiKeyResponseDTO;

public final class IssueApiKeyPresenter {

  private IssueApiKeyPresenter() {}

  public static IssueApiKeyResponseDTO present(IssueApiKeyUseCase.Output output) {
    return new IssueApiKeyResponseDTO(
        output.id(),
        output.applicationId(),
        output.label(),
        output.prefix(),
        output.plainSecret(),
        output.createdAt());
  }
}
