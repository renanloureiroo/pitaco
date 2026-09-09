package com.renanloureiroo.pitaco.modules.survey.domain.entities;

import com.renanloureiroo.pitaco.core.entity.Entity;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import java.time.Instant;
import java.util.Optional;
import lombok.Getter;

@Getter
public final class SurveyStateTransition extends Entity<SurveyStateTransitionId> {

  private final SurveyId surveyId;
  private final SurveyState from;
  private final SurveyState to;
  private final TransitionReason reason;
  private final String actor;
  private final Instant occurredAt;

  private SurveyStateTransition(
      SurveyStateTransitionId id,
      SurveyId surveyId,
      SurveyState from,
      SurveyState to,
      TransitionReason reason,
      String actor,
      Instant occurredAt) {
    super(id);
    this.surveyId = surveyId;
    this.from = from;
    this.to = to;
    this.reason = reason;
    this.actor = actor;
    this.occurredAt = occurredAt;
  }

  public static SurveyStateTransition record(
      SurveyId surveyId,
      SurveyState from,
      SurveyState to,
      TransitionReason reason,
      Instant occurredAt) {
    return new SurveyStateTransition(
        SurveyStateTransitionId.generate(), surveyId, from, to, reason, null, occurredAt);
  }

  public static SurveyStateTransition restore(
      SurveyStateTransitionId id,
      SurveyId surveyId,
      SurveyState from,
      SurveyState to,
      TransitionReason reason,
      String actor,
      Instant occurredAt) {
    return new SurveyStateTransition(id, surveyId, from, to, reason, actor, occurredAt);
  }

  // Reservado até haver autenticação: a coluna existe, o valor não.
  public Optional<String> actor() {
    return Optional.ofNullable(actor);
  }
}
