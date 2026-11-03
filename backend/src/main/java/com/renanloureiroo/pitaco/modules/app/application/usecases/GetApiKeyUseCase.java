package com.renanloureiroo.pitaco.modules.app.application.usecases;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.app.application.errors.ApiKeyNotFound;
import com.renanloureiroo.pitaco.modules.app.application.errors.ApplicationNotFound;
import com.renanloureiroo.pitaco.modules.app.application.repositories.ApiKeyRepository;
import com.renanloureiroo.pitaco.modules.app.application.repositories.ApplicationRepository;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApiKeyId;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApiKeyStatus;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import java.time.Instant;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GetApiKeyUseCase implements UseCase<GetApiKeyUseCase.Input, GetApiKeyUseCase.Output> {

  private final ApplicationRepository applicationRepository;
  private final ApiKeyRepository apiKeyRepository;

  public GetApiKeyUseCase(
      ApplicationRepository applicationRepository, ApiKeyRepository apiKeyRepository) {
    this.applicationRepository = applicationRepository;
    this.apiKeyRepository = apiKeyRepository;
  }

  @Override
  public Output execute(Input input) {
    var applicationId = applicationIdOf(input.applicationId());

    // Só a existência é consultada: aplicação inativa não pode emitir, mas pode ser enxergada.
    if (applicationRepository.findById(applicationId).isEmpty()) {
      throw new ApplicationNotFound(input.applicationId());
    }

    var apiKey =
        apiKeyRepository
            .findByIdAndApplicationId(apiKeyIdOf(input.apiKeyId()), applicationId)
            .orElseThrow(() -> new ApiKeyNotFound(input.apiKeyId()));

    log.info(
        "Chave de API consultada [{}] application={}", apiKey.id().value(), applicationId.value());

    return new Output(
        apiKey.id().value(),
        apiKey.getApplicationId().value(),
        apiKey.getLabel().value(),
        apiKey.getSecret().prefix(),
        apiKey.status(),
        apiKey.getCreatedAt(),
        apiKey.revokedAt());
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

  private ApiKeyId apiKeyIdOf(String value) {
    try {
      return ApiKeyId.of(value);
    } catch (DomainException malformed) {
      throw new ApiKeyNotFound(value);
    }
  }

  public record Input(String applicationId, String apiKeyId) {}

  public record Output(
      String id,
      String applicationId,
      String label,
      String prefix,
      ApiKeyStatus status,
      Instant createdAt,
      Optional<Instant> revokedAt) {}
}
