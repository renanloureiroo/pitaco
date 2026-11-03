package com.renanloureiroo.pitaco.testsupport.repositories;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.pagination.Page;
import com.renanloureiroo.pitaco.modules.app.application.repositories.ApiKeyRepository;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApiKey;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApiKeyId;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryApiKeyRepository implements ApiKeyRepository {

  private final Map<ApiKeyId, ApiKey> apiKeys = new LinkedHashMap<>();

  private boolean loseNextRevoke;
  private int touches;

  @Override
  public ApiKey create(ApiKey apiKey) {
    apiKeys.put(apiKey.id(), copyOf(apiKey));
    return apiKey;
  }

  @Override
  public Optional<ApiKey> findByIdAndApplicationId(ApiKeyId id, ApplicationId applicationId) {
    return Optional.ofNullable(apiKeys.get(id))
        .filter(apiKey -> apiKey.getApplicationId().equals(applicationId))
        .map(InMemoryApiKeyRepository::copyOf);
  }

  @Override
  public Optional<ApiKey> findActiveBySecretHash(String secretHash) {
    return apiKeys.values().stream()
        .filter(apiKey -> apiKey.getSecret().hash().equals(secretHash))
        .filter(apiKey -> !apiKey.isRevoked())
        .findFirst()
        .map(InMemoryApiKeyRepository::copyOf);
  }

  @Override
  public void touch(ApiKeyId id, Instant now) {
    touches++;

    var stored = apiKeys.get(id);
    if (stored == null) {
      return;
    }

    apiKeys.put(
        id,
        ApiKey.restore(
            stored.id(),
            stored.getApplicationId(),
            stored.getLabel(),
            stored.getSecret(),
            stored.getCreatedAt(),
            stored.getRevokedAt(),
            now));
  }

  public int touches() {
    return touches;
  }

  @Override
  public boolean revoke(ApiKey apiKey) {
    var stored = apiKeys.get(apiKey.id());

    if (loseNextRevoke) {
      loseNextRevoke = false;
      stored.revoke();
      return false;
    }
    if (stored == null || stored.isRevoked()) {
      return false;
    }

    apiKeys.put(apiKey.id(), copyOf(apiKey));
    return true;
  }

  @Override
  public Page<ApiKey> findPage(Query query) {
    var matching =
        apiKeys.values().stream()
            .filter(apiKey -> apiKey.getApplicationId().equals(query.applicationId()))
            .filter(apiKey -> query.status().map(apiKey.status()::equals).orElse(true))
            .sorted(
                Comparator.comparing(ApiKey::getCreatedAt)
                    .thenComparing(apiKey -> apiKey.id().value())
                    .reversed())
            .toList();

    var items =
        matching.stream()
            .skip(query.offset())
            .limit(query.size())
            .map(InMemoryApiKeyRepository::copyOf)
            .toList();

    return new Page<>(items, matching.size());
  }

  // A próxima revogação perde a corrida: a linha é revogada por outro antes do update.
  public void loseNextRevoke() {
    this.loseNextRevoke = true;
  }

  public List<ApiKey> findAll() {
    return List.copyOf(apiKeys.values());
  }

  public boolean isEmpty() {
    return apiKeys.isEmpty();
  }

  private static ApiKey copyOf(ApiKey apiKey) {
    return ApiKey.restore(
        apiKey.id(),
        apiKey.getApplicationId(),
        apiKey.getLabel(),
        apiKey.getSecret(),
        apiKey.getCreatedAt(),
        apiKey.getRevokedAt(),
        apiKey.getLastUsedAt());
  }
}
