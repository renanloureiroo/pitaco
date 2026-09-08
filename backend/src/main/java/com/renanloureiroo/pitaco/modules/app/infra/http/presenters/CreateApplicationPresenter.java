package com.renanloureiroo.pitaco.modules.app.infra.http.presenters;

import com.renanloureiroo.pitaco.modules.app.application.usecases.CreateApplicationUseCase;
import com.renanloureiroo.pitaco.modules.app.infra.http.dtos.CreateApplicationResponseDTO;

public final class CreateApplicationPresenter {

  private CreateApplicationPresenter() {}

  public static CreateApplicationResponseDTO present(CreateApplicationUseCase.Output output) {
    return new CreateApplicationResponseDTO(output.id(), output.slug());
  }
}
