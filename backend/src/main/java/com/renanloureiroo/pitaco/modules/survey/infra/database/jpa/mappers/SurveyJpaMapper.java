package com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.mappers;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.Survey;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyLifecycle;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SurveyName;
import com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.entities.SurveyJpaEntity;

public final class SurveyJpaMapper {

  private SurveyJpaMapper() {}

  public static SurveyJpaEntity toJpa(Survey survey) {
    return new SurveyJpaEntity(
        survey.id().value(),
        survey.getApplicationId().value(),
        survey.getName().value(),
        survey.getLifecycle().name(),
        survey.publishedVersionNumber().orElse(null),
        survey.draftVersionNumber().orElse(null),
        survey.getCreatedAt());
  }

  public static Survey toDomain(SurveyJpaEntity entity) {
    return Survey.restore(
        SurveyId.of(entity.getId()),
        ApplicationId.of(entity.getApplicationId()),
        SurveyName.of(entity.getName()),
        SurveyLifecycle.valueOf(entity.getLifecycle()),
        entity.getPublishedVersionNumber(),
        entity.getDraftVersionNumber(),
        entity.getCreatedAt());
  }
}
