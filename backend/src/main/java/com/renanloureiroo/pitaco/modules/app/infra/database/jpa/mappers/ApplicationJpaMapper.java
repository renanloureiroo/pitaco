package com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers;

import com.renanloureiroo.pitaco.modules.app.domain.entities.Application;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApplicationId;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.Name;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.Slug;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.entities.ApplicationJpaEntity;

public final class ApplicationJpaMapper {

  private ApplicationJpaMapper() {}

  public static ApplicationJpaEntity toJpa(Application application) {
    return new ApplicationJpaEntity(
        application.id().value(),
        application.getSlug().value(),
        application.getName().value(),
        application.getStatus(),
        application.getQuietPeriodDays(),
        application.getRetentionDays(),
        application.getOpenTextRetentionDays(),
        application.getCreatedAt(),
        application.getUpdatedAt());
  }

  public static Application toDomain(ApplicationJpaEntity entity) {
    return Application.restore(
        ApplicationId.of(entity.getId()),
        Slug.of(entity.getSlug()),
        Name.of(entity.getName()),
        entity.getStatus(),
        entity.getQuietPeriodDays(),
        entity.getRetentionDays(),
        entity.getOpenTextRetentionDays(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }
}
