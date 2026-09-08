package com.renanloureiroo.pitaco.testsupport.factories;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.app.application.repositories.ApiKeyRepository;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApiKey;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApiKeyId;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.ApiKeyLabel;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.ApiKeySecret;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

public final class ApiKeyFactory {

  public static final String DEFAULT_LABEL = "app iOS";
  public static final Instant BATCH_FIRST_CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

  private ApplicationId applicationId = ApplicationId.generate();
  private String label = DEFAULT_LABEL;
  private boolean revoked;
  private Instant createdAt;

  private ApiKeyFactory() {}

  public static ApiKeyFactory anApiKey() {
    return new ApiKeyFactory();
  }

  public ApiKeyFactory forApplication(ApplicationId applicationId) {
    this.applicationId = applicationId;
    return this;
  }

  public ApiKeyFactory withLabel(String label) {
    this.label = label;
    return this;
  }

  public ApiKeyFactory revoked() {
    this.revoked = true;
    return this;
  }

  public ApiKeyFactory createdAt(Instant createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  public ApiKey.Issued issue() {
    var issued = ApiKey.issue(applicationId, ApiKeyLabel.of(label));

    if (revoked) {
      issued.apiKey().revoke();
    }

    return issued;
  }

  // Com createdAt controlado o caminho é restore: issue carimba Instant.now() e a ordenação
  // deixaria de ser verificável.
  public ApiKey build() {
    if (createdAt == null) {
      return issue().apiKey();
    }

    return ApiKey.restore(
        ApiKeyId.generate(),
        applicationId,
        ApiKeyLabel.of(label),
        ApiKeySecret.generate().secret(),
        createdAt,
        revoked ? createdAt.plusSeconds(1) : null);
  }

  public ApiKey buildSavedIn(ApiKeyRepository repository) {
    return repository.create(build());
  }

  // Lote em ordem crescente de criação: a listagem deve devolvê-lo exatamente ao contrário.
  public List<ApiKey> buildBatchSavedIn(ApiKeyRepository repository, int count) {
    return IntStream.range(0, count)
        .mapToObj(
            position ->
                anApiKey()
                    .forApplication(applicationId)
                    .withLabel("chave " + position)
                    .createdAt(BATCH_FIRST_CREATED_AT.plusSeconds(position))
                    .buildSavedIn(repository))
        .toList();
  }
}
