package com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.mappers;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.RespondentId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayId;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayOutcome;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.SurveyDisplay;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.AttributeSnapshot;
import com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.entities.SurveyDisplayAttributeJpaEntity;
import com.renanloureiroo.pitaco.modules.collect.infra.database.jpa.entities.SurveyDisplayJpaEntity;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.stream.Collectors;

public final class SurveyDisplayMapper {

  private SurveyDisplayMapper() {}

  public static SurveyDisplayJpaEntity toJpa(SurveyDisplay display) {
    var attributes =
        display.getAttributes().values().entrySet().stream()
            .map(entry -> new SurveyDisplayAttributeJpaEntity(entry.getKey(), entry.getValue()))
            .collect(Collectors.toCollection(LinkedHashSet::new));

    return new SurveyDisplayJpaEntity(
        display.id().value(),
        display.getApplicationId().value(),
        display.getRespondentId().value(),
        display.getSurveyId().value(),
        display.getVersionId().value(),
        display.getComparabilityGroup(),
        display.getOutcome().name(),
        display.sdkVersion().orElse(null),
        display.getOpenedAt(),
        display.closedAt().orElse(null),
        attributes);
  }

  public static SurveyDisplay toDomain(SurveyDisplayJpaEntity entity) {
    var attributes = new LinkedHashMap<String, String>();
    entity
        .getAttributes()
        .forEach(attribute -> attributes.put(attribute.getName(), attribute.getValue()));

    return SurveyDisplay.restore(
        DisplayId.of(entity.getId()),
        ApplicationId.of(entity.getApplicationId()),
        RespondentId.of(entity.getRespondentId()),
        SurveyId.of(entity.getSurveyId()),
        SurveyVersionId.of(entity.getVersionId()),
        entity.getComparabilityGroup(),
        DisplayOutcome.valueOf(entity.getOutcome()),
        Optional.ofNullable(entity.getSdkVersion()),
        AttributeSnapshot.of(attributes),
        entity.getOpenedAt(),
        Optional.ofNullable(entity.getClosedAt()));
  }
}
