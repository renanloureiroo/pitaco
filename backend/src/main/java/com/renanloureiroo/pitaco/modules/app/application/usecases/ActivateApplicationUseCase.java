package com.renanloureiroo.pitaco.modules.app.application.usecases;

import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.app.application.errors.ApplicationNotFound;
import com.renanloureiroo.pitaco.modules.app.application.outputs.ApplicationOutput;
import com.renanloureiroo.pitaco.modules.app.application.repositories.ApplicationRepository;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ActivateApplicationUseCase
    implements UseCase<ActivateApplicationUseCase.Input, ApplicationOutput> {

  private final ApplicationRepository applications;

  public ActivateApplicationUseCase(ApplicationRepository applications) {
    this.applications = applications;
  }

  public record Input(String applicationId) {}

  @Override
  public ApplicationOutput execute(Input input) {
    var application =
        applications
            .findById(GetApplicationUseCase.applicationIdOf(input.applicationId()))
            .orElseThrow(() -> new ApplicationNotFound(input.applicationId()));

    if (application.isActive()) {
      return ApplicationOutput.of(application);
    }

    application.activate();
    var saved = applications.update(application);

    log.info("Aplicação reativada [{}]", saved.id().value());

    return ApplicationOutput.of(saved);
  }
}
