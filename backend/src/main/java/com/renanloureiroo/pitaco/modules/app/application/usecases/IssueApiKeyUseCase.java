package com.renanloureiroo.pitaco.modules.app.application.usecases;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.app.application.errors.ApplicationIsInactive;
import com.renanloureiroo.pitaco.modules.app.application.errors.ApplicationNotFound;
import com.renanloureiroo.pitaco.modules.app.application.repositories.ApiKeyRepository;
import com.renanloureiroo.pitaco.modules.app.application.repositories.ApplicationRepository;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApiKey;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.ApiKeyLabel;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IssueApiKeyUseCase
    implements UseCase<IssueApiKeyUseCase.Input, IssueApiKeyUseCase.Output> {

  private final ApplicationRepository applicationRepository;
  private final ApiKeyRepository apiKeyRepository;

  public IssueApiKeyUseCase(
      ApplicationRepository applicationRepository, ApiKeyRepository apiKeyRepository) {
    this.applicationRepository = applicationRepository;
    this.apiKeyRepository = apiKeyRepository;
  }

  @Override
  public Output execute(Input input) {
    var applicationId = applicationIdOf(input.applicationId());

    var application =
        applicationRepository
            .findById(applicationId)
            .orElseThrow(() -> new ApplicationNotFound(input.applicationId()));

    if (!application.isActive()) {
      throw new ApplicationIsInactive(applicationId);
    }

    var issued = ApiKey.issue(applicationId, ApiKeyLabel.of(input.label()));
    var apiKey = issued.apiKey();

    apiKeyRepository.create(apiKey);

    log.info(
        "Chave de API emitida [{}] application={} prefix={}",
        apiKey.id().value(),
        applicationId.value(),
        apiKey.getSecret().prefix());

    return new Output(
        apiKey.id().value(),
        applicationId.value(),
        apiKey.getLabel().value(),
        apiKey.getSecret().prefix(),
        issued.plainSecret(),
        apiKey.getCreatedAt());
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

  public record Input(String applicationId, String label) {}

  public record Output(
      String id,
      String applicationId,
      String label,
      String prefix,
      String plainSecret,
      Instant createdAt) {}
}
