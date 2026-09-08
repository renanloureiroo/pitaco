package com.renanloureiroo.pitaco.modules.app.application.usecases;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.transaction.Transactional;
import com.renanloureiroo.pitaco.core.usecase.UseCaseWithoutOutput;
import com.renanloureiroo.pitaco.modules.app.application.errors.ApiKeyNotFound;
import com.renanloureiroo.pitaco.modules.app.application.repositories.ApiKeyRepository;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApiKey;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApiKeyId;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RevokeApiKeyUseCase implements UseCaseWithoutOutput<RevokeApiKeyUseCase.Input> {

  private final ApiKeyRepository apiKeyRepository;

  public RevokeApiKeyUseCase(ApiKeyRepository apiKeyRepository) {
    this.apiKeyRepository = apiKeyRepository;
  }

  @Override
  @Transactional
  public void execute(Input input) {
    var applicationId = applicationIdOf(input);
    var apiKeyId = apiKeyIdOf(input);

    var apiKey = find(apiKeyId, applicationId, input);
    apiKey.revoke();

    if (!apiKeyRepository.revoke(apiKey)) {
      find(apiKeyId, applicationId, input).revoke();
    }

    log.info("Chave de API revogada [{}] application={}", apiKeyId.value(), applicationId.value());
  }

  private ApiKey find(ApiKeyId apiKeyId, ApplicationId applicationId, Input input) {
    return apiKeyRepository
        .findByIdAndApplicationId(apiKeyId, applicationId)
        .orElseThrow(() -> new ApiKeyNotFound(input.apiKeyId()));
  }

  // Formato inválido, chave inexistente e chave de outra aplicação respondem o mesmo: distinguir
  // entregaria um oráculo de formato e de existência a quem sonda a API.
  private ApplicationId applicationIdOf(Input input) {
    try {
      return ApplicationId.of(input.applicationId());
    } catch (DomainException malformed) {
      throw new ApiKeyNotFound(input.apiKeyId());
    }
  }

  private ApiKeyId apiKeyIdOf(Input input) {
    try {
      return ApiKeyId.of(input.apiKeyId());
    } catch (DomainException malformed) {
      throw new ApiKeyNotFound(input.apiKeyId());
    }
  }

  public record Input(String applicationId, String apiKeyId) {}
}
