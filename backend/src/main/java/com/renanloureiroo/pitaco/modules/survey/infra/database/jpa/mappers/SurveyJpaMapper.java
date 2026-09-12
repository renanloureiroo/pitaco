package com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.mappers;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.Survey;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyLifecycle;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.Exposure;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.FreeTextNotice;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SurveyName;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SurveyTemplate;
import com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.entities.SurveyJpaEntity;
import java.util.Optional;

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
        survey.getExposure().priority(),
        survey.getExposure().responseQuota().orElse(null),
        survey.getExposure().ignoresQuietPeriod(),
        survey.template().map(Enum::name).orElse(null),
        survey.getFreeTextNotice().enabled(),
        survey.getFreeTextNotice().customText().orElse(null),
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
        new Exposure(
            entity.getPriority(),
            Optional.ofNullable(entity.getResponseQuota()),
            entity.isIgnoresQuietPeriod()),
        Optional.ofNullable(entity.getTemplateKind()).map(SurveyTemplate::valueOf),
        new FreeTextNotice(
            entity.isFreeTextNoticeEnabled(), Optional.ofNullable(entity.getFreeTextNoticeText())),
        entity.getCreatedAt());
  }
}
