package com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.mappers;

import com.renanloureiroo.pitaco.core.catalog.EventName;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.ObservedEvent;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.ObservedEventId;
import com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.entities.ApplicationEventJpaEntity;

public final class ObservedEventMapper {

  private ObservedEventMapper() {}

  public static ObservedEvent toDomain(ApplicationEventJpaEntity entity) {
    return ObservedEvent.restore(
        ObservedEventId.of(entity.getId()),
        ApplicationId.of(entity.getApplicationId()),
        EventName.of(entity.getName()),
        entity.getFirstSeenAt(),
        entity.getLastSeenAt());
  }
}
