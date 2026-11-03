package com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.mappers;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.Respondent;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.RespondentId;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.RespondentIdentity;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.RespondentIdentityKind;
import com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.entities.RespondentJpaEntity;

public final class RespondentMapper {

  private RespondentMapper() {}

  public static RespondentJpaEntity toJpa(Respondent respondent) {
    return new RespondentJpaEntity(
        respondent.id().value(),
        respondent.getApplicationId().value(),
        respondent.getIdentity().kind().name(),
        respondent.getIdentity().value(),
        respondent.getFirstSeenAt(),
        respondent.getLastSeenAt());
  }

  public static Respondent toDomain(RespondentJpaEntity entity) {
    return Respondent.restore(
        RespondentId.of(entity.getId()),
        ApplicationId.of(entity.getApplicationId()),
        new RespondentIdentity(
            RespondentIdentityKind.valueOf(entity.getIdentityKind()), entity.getIdentityValue()),
        entity.getFirstSeenAt(),
        entity.getLastSeenAt());
  }
}
