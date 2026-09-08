package com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers;

import com.renanloureiroo.pitaco.modules.app.domain.entities.ApiKey;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApiKeyId;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApplicationId;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.ApiKeyLabel;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.ApiKeySecret;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.entities.ApiKeyJpaEntity;

public final class ApiKeyJpaMapper {

  private ApiKeyJpaMapper() {}

  public static ApiKeyJpaEntity toJpa(ApiKey apiKey) {
    return new ApiKeyJpaEntity(
        apiKey.id().value(),
        apiKey.getApplicationId().value(),
        apiKey.getLabel().value(),
        apiKey.getSecret().prefix(),
        apiKey.getSecret().hash(),
        apiKey.getCreatedAt(),
        apiKey.getRevokedAt());
  }

  public static ApiKey toDomain(ApiKeyJpaEntity entity) {
    return ApiKey.restore(
        ApiKeyId.of(entity.getId()),
        ApplicationId.of(entity.getApplicationId()),
        ApiKeyLabel.of(entity.getLabel()),
        new ApiKeySecret(entity.getPrefix(), entity.getSecretHash()),
        entity.getCreatedAt(),
        entity.getRevokedAt());
  }
}
