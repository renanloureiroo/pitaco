package com.renanloureiroo.pitaco.modules.app.application.usecases;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.app.application.errors.ApplicationNotFound;
import com.renanloureiroo.pitaco.modules.app.application.repositories.ApplicationRepository;
import com.renanloureiroo.pitaco.modules.app.domain.entities.Status;
import java.time.Instant;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GetApplicationUseCase
    implements UseCase<GetApplicationUseCase.Input, GetApplicationUseCase.Output> {

  private final ApplicationRepository applications;

  public GetApplicationUseCase(ApplicationRepository applications) {
    this.applications = applications;
  }

  @Override
  public Output execute(Input input) {
    var application =
        applications
            .findById(applicationIdOf(input.applicationId()))
            .orElseThrow(() -> new ApplicationNotFound(input.applicationId()));

    log.info("Aplicação consultada [{}]", application.id().value());

    return new Output(
        application.id().value(),
        application.getSlug().value(),
        application.getName().value(),
        application.getStatus(),
        application.quietPeriodDays(),
        application.retentionDays(),
        application.openTextRetentionDays(),
        application.getCreatedAt(),
        application.getUpdatedAt());
  }

  // Formato inválido é indistinguível de inexistente: distinguir entregaria um oráculo de
  // formato a quem sonda a API.
  private ApplicationId applicationIdOf(String value) {
    try {
      return ApplicationId.of(value);
    } catch (DomainException malformed) {
      throw new ApplicationNotFound(value);
    }
  }

  public record Input(String applicationId) {}

  public record Output(
      String id,
      String slug,
      String name,
      Status status,
      Optional<Integer> quietPeriodDays,
      Optional<Integer> retentionDays,
      Optional<Integer> openTextRetentionDays,
      Instant createdAt,
      Instant updatedAt) {}
}
