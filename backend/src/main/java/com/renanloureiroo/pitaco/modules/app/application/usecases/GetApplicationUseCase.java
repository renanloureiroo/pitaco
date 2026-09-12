package com.renanloureiroo.pitaco.modules.app.application.usecases;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.app.application.errors.ApplicationNotFound;
import com.renanloureiroo.pitaco.modules.app.application.outputs.ApplicationOutput;
import com.renanloureiroo.pitaco.modules.app.application.repositories.ApplicationRepository;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GetApplicationUseCase
    implements UseCase<GetApplicationUseCase.Input, ApplicationOutput> {

  private final ApplicationRepository applications;

  public GetApplicationUseCase(ApplicationRepository applications) {
    this.applications = applications;
  }

  @Override
  public ApplicationOutput execute(Input input) {
    var application =
        applications
            .findById(applicationIdOf(input.applicationId()))
            .orElseThrow(() -> new ApplicationNotFound(input.applicationId()));

    log.info("Aplicação consultada [{}]", application.id().value());

    return ApplicationOutput.of(application);
  }

  // Formato inválido é indistinguível de inexistente: distinguir entregaria um oráculo de
  // formato a quem sonda a API.
  static ApplicationId applicationIdOf(String value) {
    try {
      return ApplicationId.of(value);
    } catch (DomainException malformed) {
      throw new ApplicationNotFound(value);
    }
  }

  public record Input(String applicationId) {}
}
