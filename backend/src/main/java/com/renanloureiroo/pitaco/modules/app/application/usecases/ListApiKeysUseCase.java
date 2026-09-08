package com.renanloureiroo.pitaco.modules.app.application.usecases;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.app.application.errors.ApplicationNotFound;
import com.renanloureiroo.pitaco.modules.app.application.repositories.ApiKeyRepository;
import com.renanloureiroo.pitaco.modules.app.application.repositories.ApplicationRepository;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApiKey;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApiKeyStatus;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApplicationId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ListApiKeysUseCase
    implements UseCase<ListApiKeysUseCase.Input, ListApiKeysUseCase.Output> {

  private final ApplicationRepository applicationRepository;
  private final ApiKeyRepository apiKeyRepository;

  public ListApiKeysUseCase(
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

    var page =
        apiKeyRepository.findPage(
            new ApiKeyRepository.Query(
                applicationId, input.status(), input.page(), input.size()));

    log.info(
        "Chaves de API listadas application={} status={} total={}",
        applicationId.value(),
        input.status().map(Enum::name).orElse("ALL"),
        page.total());

    return new Output(
        page.map(ListApiKeysUseCase::itemOf).items(),
        input.page(),
        input.size(),
        page.total(),
        page.totalPages(input.size()));
  }

  private static Item itemOf(ApiKey apiKey) {
    return new Item(
        apiKey.id().value(),
        apiKey.getApplicationId().value(),
        apiKey.getLabel().value(),
        apiKey.getSecret().prefix(),
        apiKey.status(),
        apiKey.getCreatedAt(),
        apiKey.revokedAt());
  }

  private ApplicationId applicationIdOf(String value) {
    try {
      return ApplicationId.of(value);
    } catch (DomainException malformed) {
      throw new ApplicationNotFound(value);
    }
  }

  public record Input(
      String applicationId, Optional<ApiKeyStatus> status, int page, int size) {}

  public record Output(List<Item> items, int page, int size, long total, int totalPages) {}

  public record Item(
      String id,
      String applicationId,
      String label,
      String prefix,
      ApiKeyStatus status,
      Instant createdAt,
      Optional<Instant> revokedAt) {}
}
