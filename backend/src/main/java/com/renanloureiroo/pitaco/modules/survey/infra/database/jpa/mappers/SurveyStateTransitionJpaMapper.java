package com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.mappers;

import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyId;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyState;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyStateTransition;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyStateTransitionId;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.TransitionReason;
import com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.entities.SurveyStateTransitionJpaEntity;

public final class SurveyStateTransitionJpaMapper {

  private SurveyStateTransitionJpaMapper() {}

  public static SurveyStateTransitionJpaEntity toJpa(SurveyStateTransition transition) {
    return new SurveyStateTransitionJpaEntity(
        transition.id().value(),
        transition.getSurveyId().value(),
        transition.getFrom().name(),
        transition.getTo().name(),
        transition.getReason().name(),
        transition.actor().orElse(null),
        transition.getOccurredAt());
  }

  public static SurveyStateTransition toDomain(SurveyStateTransitionJpaEntity entity) {
    return SurveyStateTransition.restore(
        SurveyStateTransitionId.of(entity.getId()),
        SurveyId.of(entity.getSurveyId()),
        SurveyState.valueOf(entity.getFromState()),
        SurveyState.valueOf(entity.getToState()),
        TransitionReason.valueOf(entity.getReason()),
        entity.getActor(),
        entity.getOccurredAt());
  }
}
